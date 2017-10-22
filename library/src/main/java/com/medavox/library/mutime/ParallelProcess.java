package com.medavox.library.mutime;

/**
 * Created by scc on 21/10/17.
 */
class ParallelProcess<In, Out> extends Thread {
    private int numThreads = -1;
    private Thread[] threads;
    private In inputSingle = null;
    private In[] inputArray = null;
    private Out[] output;
    private boolean arrayInputMode;

    public ParallelProcess(In[] input, Out[] output) {
        if (input.length != output.length) {
            throw new IllegalArgumentException("input array length must match output array length");
        }
        arrayInputMode = true;
        this.inputArray = input;
        this.output = output;
        numThreads = input.length;
    }

    public ParallelProcess(In input, Out[] output) {
        this.inputSingle = input;
        this.output = output;
        arrayInputMode = false;
        numThreads = output.length;
    }

    public void doWork(Worker smug) {
        threads = new Thread[numThreads];
        if(arrayInputMode) {
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Wrapper(inputArray[i], output[i], smug);
                threads[i].start();
            }
        }
        else {
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Wrapper(inputSingle, output[i], smug);
                threads[i].start();
            }
        }
    }

    public void waitTillFinished() {
        for (Thread t : threads) {
            try {
                if(t != null) {
                    t.join();
                }
            } catch (InterruptedException ie) {
                System.err.println("" + ie);
                ie.printStackTrace();
            }
        }
    }

    private class Wrapper extends Thread {
        private In in;
        private Out out;
        private Worker worker;
        public Wrapper(In inn, Out aus, Worker worker) {
            in = inn;
            out = aus;
            this.worker = worker;
        }
        @Override
        public void run() {
            super.run();
            worker.performProcess(in, out);
        }
    }

    public interface Worker<In, Out> {
        void performProcess(In input, Out output);
    }
}
