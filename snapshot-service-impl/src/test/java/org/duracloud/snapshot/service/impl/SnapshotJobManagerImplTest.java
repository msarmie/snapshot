/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.snapshot.service.impl.BatchJobBuilderManager;
import org.duracloud.snapshot.service.impl.SnapshotJobBuilder;
import org.duracloud.snapshot.service.impl.SnapshotJobManagerImpl;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.amazonaws.services.elasticache.model.SnapshotNotFoundException;

/**
 * @author Daniel Bernstein Date: Feb 19, 2014
 */
public class SnapshotJobManagerImplTest extends SnapshotTestBase {

    private String snapshotName = "test-id";
    private Long snapshotId = 10101l;

    @TestSubject
    private SnapshotJobManagerImpl manager;

    @Mock
    private JobExecutionListener snapshotJobListener;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TaskExecutor taskExecutor;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job job;

    @Mock
    private Snapshot snapshot;

    @Mock
    private Restoration restoration;

    @Mock
    private ApplicationContext context;

    @Mock
    private SnapshotJobManagerConfig config;

    @Mock
    private RestoreRepo restoreRepo;

    @Mock
    private SnapshotRepo snapshotRepo;

    @Mock
    private SnapshotJobBuilder snapshotJobBuilder;

    @Mock
    private BatchJobBuilderManager builderManager;

    @Before
    public void setup() {
        manager =
            new SnapshotJobManagerImpl(snapshotRepo,
                                       restoreRepo,
                                       jobLauncher,
                                       jobRepository,
                                       builderManager);
        manager.init(config);
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    @Test
    public void testExecuteSnapshot() throws Exception {

        EasyMock.expect(snapshotJobBuilder.buildJob(snapshot, config))
                .andReturn(job);

        EasyMock.expect(snapshotJobBuilder.buildJobParameters(snapshot))
                .andReturn(new JobParameters());

        EasyMock.expect(jobLauncher.run(EasyMock.isA(Job.class),
                                        EasyMock.isA(JobParameters.class)))
                .andReturn(jobExecution);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);

        setupSnapshotRepo();

        setupBuilderManager();

        replayAll();

        manager.executeSnapshot(snapshotName);

    }

    /**
     * 
     */
    private void setupSnapshotRepo() {
        EasyMock.expect(snapshotRepo.findByName(snapshotName))
                .andReturn(snapshot);
    }

    /**
     * 
     */
    private void setupBuilderManager() {
        EasyMock.expect(builderManager.getBuilder(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshotJobBuilder);
    }

    @Test
    public void testGetSnapshotStatus()
        throws SnapshotNotFoundException,
            SnapshotException {

        EasyMock.expect(snapshotJobBuilder.buildIdentifyingJobParameters(snapshot))
                .andReturn(new JobParameters());
        
        setupBuilderManager();

        EasyMock.expect(jobRepository.getLastJobExecution(EasyMock.isA(String.class),
                                                          EasyMock.isA(JobParameters.class)))
                .andReturn(jobExecution);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);

        setupSnapshotRepo();

        replayAll();
        Assert.assertNotNull(this.manager.getStatus(snapshotName));
    }

}