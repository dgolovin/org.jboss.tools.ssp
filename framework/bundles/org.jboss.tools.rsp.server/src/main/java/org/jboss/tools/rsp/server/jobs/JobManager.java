package org.jboss.tools.rsp.server.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.tools.rsp.eclipse.core.runtime.IRunnableWithProgress;
import org.jboss.tools.rsp.eclipse.core.runtime.IStatus;
import org.jboss.tools.rsp.eclipse.core.runtime.Status;
import org.jboss.tools.rsp.launching.utils.IStatusRunnableWithProgress;
import org.jboss.tools.rsp.server.ServerCoreActivator;
import org.jboss.tools.rsp.server.spi.jobs.IJob;
import org.jboss.tools.rsp.server.spi.jobs.IJobListener;
import org.jboss.tools.rsp.server.spi.jobs.IJobManager;
import org.jboss.tools.rsp.server.spi.jobs.SimpleJob;

public class JobManager implements IJobManager {

	private List<IJobListener> listeners = new ArrayList<>();
	private Map<String, IJob> currentJobs = new HashMap<>();
	private ExecutorService executor = Executors.newFixedThreadPool(5);
	
	public JobManager() {
		super();
	}
	@Override
	public void addJobListener(IJobListener l) {
		listeners.add(l);
	}

	@Override
	public void removeJobListener(IJobListener l) {
		listeners.remove(l);
	}

	@Override
	public IJob scheduleJob(String jobName, IRunnableWithProgress runnable) {
		SimpleJob job = new SimpleJob(jobName, generateJobId(), runnable);
		currentJobs.put(job.getId(), job);
		fireJobAdded(job);
		schedule(job);
		return job;
	}

	@Override
	public IJob scheduleJob(String jobName, IStatusRunnableWithProgress runnable) {
		SimpleJob job = new SimpleJob(jobName, generateJobId(), runnable);
		currentJobs.put(job.getId(), job);
		fireJobAdded(job);
		schedule(job);
		return job;
	}
	
	private void fireJobAdded(IJob job) {
		ArrayList<IJobListener> tmp = new ArrayList<>(listeners);
		for( IJobListener l : tmp ) {
			l.jobAdded(job);
		}
	}

	private void schedule(SimpleJob job) {
		executor.execute(() -> {
			IStatus s = null;
			try {
				s = job.run();
			} catch(Exception e) {
				s = new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, e.getMessage(), e);
			}
			jobComplete(job, s);
		});
	}
	
	@Override
	public void cancel(IJob job) {
		job.getProgressMonitor().setCanceled(true);
	}
	
	private void jobComplete(SimpleJob job, IStatus s) {
		currentJobs.remove(job.getId());
		ArrayList<IJobListener> tmp = new ArrayList<>(listeners);
		for( IJobListener l : tmp ) {
			l.jobRemoved(job, s);
		}
	}
	
	private String generateJobId() {
		return UUID.randomUUID().toString();
	}

	@Override
	public void shutdown() {
		executor.shutdown();
	}

}
