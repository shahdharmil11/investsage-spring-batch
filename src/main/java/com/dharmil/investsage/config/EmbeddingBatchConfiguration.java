package com.dharmil.investsage.config;

import com.dharmil.investsage.batch.ChunkInfo;
import com.dharmil.investsage.batch.CsvInvestmentData;
import com.dharmil.investsage.batch.EmbeddingItemWriter;
import com.dharmil.investsage.batch.RawDataRecord;
import com.dharmil.investsage.batch.RawDataToChunksProcessor;
import com.dharmil.investsage.service.OpenAiEmbeddingService;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class EmbeddingBatchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBatchConfiguration.class);

    private final DataSource dataSource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OpenAiEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    public EmbeddingBatchConfiguration(DataSource dataSource,
                                       JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       OpenAiEmbeddingService embeddingService,
                                       JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------------------------------
    // CSV → raw_investment_data
    // ---------------------------------------------------------------------------------

    @Bean
    public FlatFileItemReader<CsvInvestmentData> csvReader() {
        return new FlatFileItemReaderBuilder<CsvInvestmentData>()
                .name("csvInvestmentReader")
                .resource(new ClassPathResource("cleaned_data_for_investment_advice.csv"))
                .delimited()
                .quoteCharacter('"')
                .names("instruction", "input", "output", "text")
                .linesToSkip(1)
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(CsvInvestmentData.class);
                }})
                .strict(true)  // Fail if file missing (for immediate feedback)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<CsvInvestmentData> csvWriter() {
        return new JdbcBatchItemWriterBuilder<CsvInvestmentData>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO raw_investment_data (instruction, output) VALUES (:instruction, :output)")
                .dataSource(dataSource)
                .assertUpdates(false)  // Don't fail if duplicate constraint violations occur
                .build();
    }

@Bean
public Step csvImportStep(JobRepository jobRepository,
                         PlatformTransactionManager transactionManager,
                         FlatFileItemReader<CsvInvestmentData> csvReader,
                         ItemWriter<CsvInvestmentData> csvWriter) {
    return new StepBuilder("csvImportStep", jobRepository)
            .<CsvInvestmentData, CsvInvestmentData>chunk(100, transactionManager)
            .reader(csvReader)
            .writer(csvWriter)
            .faultTolerant()
            .skip(FlatFileParseException.class)
            .skipLimit(Integer.MAX_VALUE)  // Never fail on parse errors
            .skip(DataAccessException.class)
            .skipLimit(Integer.MAX_VALUE)  // Never fail on DB errors
            .skip(NullPointerException.class)
            .skipLimit(Integer.MAX_VALUE)  // Never fail on null data
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)  // Catch-all for any other errors
            .listener(new StepExecutionListener() {
                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    log.warn("CSV Import completed: Read={}, Written={}, Skipped={}", 
                            stepExecution.getReadCount(),
                            stepExecution.getWriteCount(),
                            stepExecution.getSkipCount());
                    return stepExecution.getExitStatus();
                }
            })
            .build();
}

    @Bean
    @Qualifier("csvImportJob")
    public Job csvImportJob(Step csvImportStep) {
        return new JobBuilder("csvImportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(csvImportStep)
                .build();
    }

    // ---------------------------------------------------------------------------------
    // raw_investment_data → new_investment_embeddings
    // ---------------------------------------------------------------------------------

    @Bean
    public PagingQueryProvider rawDataQueryProvider() throws Exception {
        SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
        factory.setDataSource(dataSource);
        factory.setSelectClause("SELECT id, instruction, output");
        factory.setFromClause("FROM raw_investment_data");
        // Only process records not yet embedded
        factory.setWhereClause("WHERE (instruction IS NOT NULL AND LENGTH(TRIM(instruction)) > 0) OR (output IS NOT NULL AND LENGTH(TRIM(output)) > 0)");
        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        factory.setSortKeys(sortKeys);
        return factory.getObject();
    }

    @Bean
    public JdbcPagingItemReader<RawDataRecord> embeddingReader(PagingQueryProvider rawDataQueryProvider) {
        return new JdbcPagingItemReaderBuilder<RawDataRecord>()
                .name("rawDataReader")
                .dataSource(dataSource)
                .queryProvider(rawDataQueryProvider)
                .pageSize(100)
                .rowMapper(new BeanPropertyRowMapper<>(RawDataRecord.class))
                .saveState(true)  // Enable restart capability
                .build();
    }

    @Bean
    public ItemProcessor<RawDataRecord, List<ChunkInfo>> embeddingProcessor() {
        return new RawDataToChunksProcessor();
    }

    @Bean
    public ItemWriter<List<ChunkInfo>> embeddingWriter() {
        return new EmbeddingItemWriter(embeddingService, jdbcTemplate);
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("batch-");
        executor.setConcurrencyLimit(4);
        return executor;
    }

    @Bean
    public Step embeddingStep(JdbcPagingItemReader<RawDataRecord> embeddingReader,
                              ItemProcessor<RawDataRecord, List<ChunkInfo>> embeddingProcessor,
                              ItemWriter<List<ChunkInfo>> embeddingWriter,
                              TaskExecutor batchTaskExecutor) {
        return new StepBuilder("embeddingProcessingStep", jobRepository)
                .<RawDataRecord, List<ChunkInfo>>chunk(10, transactionManager)
                .reader(embeddingReader)
                .processor(embeddingProcessor)
                .writer(embeddingWriter)
                .taskExecutor(batchTaskExecutor)
                .faultTolerant()
                // Handle OpenAI API failures (rate limits, timeouts, network issues)
                .retry(Exception.class)
                .retryLimit(3)
                // Skip records that fail after retries
                .skip(Exception.class)
                .skipLimit(50)
                .listener(new StepExecutionListener() {
                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    log.info("Embedding generation completed: Processed={}, Skipped={}", 
                            stepExecution.getWriteCount(),
                            stepExecution.getSkipCount());
                    return stepExecution.getExitStatus();
                }
                })
                .build();
    }

    @Bean
    @Qualifier("embeddingJob")
    public Job embeddingJob(Step embeddingStep) {
        return new JobBuilder("generateEmbeddingsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(embeddingStep)
                .build();
    }
}