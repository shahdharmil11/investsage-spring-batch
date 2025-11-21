package com.dharmil.investsage.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataImportRunner.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("csvImportJob")
    private Job csvImportJob;

    @Value("${app.batch.run-data-import:false}")
    private boolean runDataImport;

    @Override
    public void run(String... args) throws Exception {
        if (runDataImport) {
            log.warn("========== CommandLineRunner: Launching CSV Data Import Job ==========");
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(csvImportJob, jobParameters);
            log.warn("========== CSV Data Import Job Completed ==========");
        } else {
            log.info("Skipping CSV Data Import Job (app.batch.run-data-import=false)");
        }
    }
}