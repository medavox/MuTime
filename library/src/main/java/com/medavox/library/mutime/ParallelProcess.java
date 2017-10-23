package com.medavox.library.mutime;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by scc on 21/10/17.
 */
class ParallelProcess<In, Out> extends Thread {
    private int numThreads = -1;
    private List<InternalWrapper> threads;
    private In inputSingle = null;
    private In[] inputArray = null;
    private boolean arrayInputMode;

    public ParallelProcess(In[] input) {
        arrayInputMode = true;
        this.inputArray = input;
        numThreads = input.length;
    }

    public ParallelProcess(In input, int numberOfThreads) {
        this.inputSingle = input;
        arrayInputMode = false;
        numThreads = numberOfThreads;
    }


    public void doWork(Worker<In, Out> smug) {
        threads = new ArrayList<InternalWrapper>(numThreads);
        if(arrayInputMode) {
            for (int i = 0; i < inputArray.length; i++) {
                InternalWrapper iw = new InternalWrapper(inputArray[i], smug);
                threads.add(i, iw);
                iw.start();
            }
        }
        else {
            for (int i = 0; i < numThreads; i++) {
                InternalWrapper iw = new InternalWrapper(inputSingle, smug);
                threads.add(i, iw);
                iw.start();
            }
        }
    }

    public void collectOutputWhenFinished(Out[] output) {
        if(output.length != numThreads) {
            throw new IllegalArgumentException("Supplied output array length must match number of" +
                " threads. Number of threads: "+numThreads+"; output array length: "+output.length);
        }
        for (int i = 0; i < threads.size(); i++) {
            try {
                InternalWrapper t = threads.get(i);
                if(t != null) {
                    t.join();
                    output[i] = t.getOutput();
                }
            } catch (InterruptedException ie) {
                System.err.println("" + ie);
                ie.printStackTrace();
            }
        }
    }

    private class InternalWrapper extends Thread {
        private In in;
        private Out out;
        private Worker<In, Out> worker;
        public InternalWrapper(In inn, Worker<In, Out> worker) {
            in = inn;
            this.worker = worker;
        }
        @Override
        public void run() {
            super.run();
            out = worker.performWork(in);
        }

        public Out getOutput() {
            return out;
        }
    }

    public interface Worker<In, Out> {
        Out performWork(In input);
    }
}
