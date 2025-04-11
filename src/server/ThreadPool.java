package server;

import java.util.LinkedList;
import java.util.Queue;
import util.Logger;

/**
 * Custom thread pool implementation for handling client connections.
 */
public class ThreadPool {
    private final WorkerThread[] threads;
    private final Queue<Runnable> taskQueue;
    private boolean isShutdown = false;

    /**
     * Creates a new thread pool with the specified number of threads.
     *
     * @param maxThreads the maximum number of threads in the pool
     */
    public ThreadPool(int maxThreads) {
        this.taskQueue = new LinkedList<>();
        this.threads = new WorkerThread[maxThreads];

        // Create and start worker threads
        for (int i = 0; i < maxThreads; i++) {
            threads[i] = new WorkerThread();
            threads[i].start();
        }

        Logger.info("Thread pool initialized with " + maxThreads + " threads");
    }

    /**
     * Submits a task to be executed by the thread pool.
     *
     * @param task the task to execute
     * @throws IllegalStateException if the thread pool is shut down
     */
    public synchronized void execute(Runnable task) {
        if (isShutdown) {
            throw new IllegalStateException("Thread pool is shut down");
        }

        taskQueue.offer(task);
        notify(); // Notify a waiting worker thread

        Logger.debug("Task submitted to thread pool. Queue size: " + taskQueue.size());
    }

    /**
     * Shuts down the thread pool, allowing existing tasks to complete.
     */
    public synchronized void shutdown() {
        isShutdown = true;
        notifyAll(); // Wake up all worker threads

        // Interrupt all worker threads
        for (WorkerThread thread : threads) {
            thread.interrupt();
        }

        Logger.info("Thread pool shutdown initiated");
    }

    /**
     * Worker thread that executes tasks from the queue.
     */
    private class WorkerThread extends Thread {
        public void run() {
            Runnable task;

            while (true) {
                synchronized (ThreadPool.this) {
                    // Wait for a task to be available
                    while (taskQueue.isEmpty() && !isShutdown) {
                        try {
                            ThreadPool.this.wait();
                        } catch (InterruptedException e) {
                            if (isShutdown) {
                                return;
                            }
                        }
                    }

                    // If the pool is shut down and the queue is empty, exit
                    if (isShutdown && taskQueue.isEmpty()) {
                        return;
                    }

                    // Get a task from the queue
                    task = taskQueue.poll();
                }

                // Execute the task
                try {
                    if (task != null) {
                        task.run();
                    }
                } catch (RuntimeException e) {
                    Logger.error("Exception in worker thread", e);
                }
            }
        }
    }
}