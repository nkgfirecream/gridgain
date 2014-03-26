//* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.hadoop;

import com.google.common.collect.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import static com.google.common.collect.Maps.*;
import static com.google.common.collect.MinMaxPriorityQueue.*;
import static java.util.Collections.*;

/**
 * Hadoop-based 10 popular words example: all files in a given directory are tokenized and for each word longer than
 * 3 characters the number of occurrences ins calculated. Finally, 10 words with the highest occurrence count are
 * output.
 *
 * NOTE: in order to run this example on Windows please ensure that cygwin is installed and available in the system
 * path.
 */
public class GridHadoopPopularWordsTest {
    /** GridGain home. */
    private static final String GG_HOME = U.getGridGainHome();

    /** The path to the input directory. ALl files in that directory will be processed. */
    private static final Path BOOKS_LOCAL_DIR =
        new Path("file:" + GG_HOME, "modules/tests/java/org/gridgain/grid/hadoop/books");

    /** The path to the output directory. THe result file will be written to this location. */
    private static final Path RESULT_LOCAL_DIR =
        new Path("file:" + GG_HOME, "modules/tests/java/org/gridgain/grid/hadoop/output");

    /** Popular books source dir in DFS. */
    private static final Path BOOKS_DFS_DIR = new Path("tmp/word-count-example/in");

    /** Popular books source dir in DFS. */
    private static final Path RESULT_DFS_DIR = new Path("tmp/word-count-example/out");

    /** Path to the distributed file system configuration. */
    private static final String DFS_CFG = "examples/config/hadoop/core-site.xml";

    /** Top N words to select **/
    private static final int POPULAR_WORDS_CNT = 10;

    /**
     * For each token in the input string the mapper emits a {word, 1} pair.
     */
    private static class TokenizingMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        /** Constant value. */
        private static final IntWritable ONE = new IntWritable(1);

        /** The word converted into the Text. */
        private Text word = new Text();

        /**
         * Emits a entry where the key is the word and the value is always 1.
         *
         * @param key the current position in the input file (not used here)
         * @param val the text string
         * @param ctx mapper context
         * @throws IOException
         * @throws InterruptedException
         */
        @Override protected void map(LongWritable key, Text val, Context ctx)
            throws IOException, InterruptedException {
            // Get the mapped object.
            final String line = val.toString();

            // Splits the given string to words.
            final String[] words = line.split("[^a-zA-Z0-9]");

            for (final String w : words) {
                // Only emit counts for longer words.
                if (w.length() <= 3)
                    continue;

                word.set(w);

                // Write the word into the context with the initial count equals 1.
                ctx.write(word, ONE);
            }
        }
    }

    /**
     * The reducer uses a priority queue to rank the words based on its number of occurrences.
     */
    private static class TopNWordsReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private MinMaxPriorityQueue<Entry<Integer, String>> q;

        TopNWordsReducer() {
            q = orderedBy(reverseOrder(new Comparator<Entry<Integer, String>>() {
                @Override public int compare(Entry<Integer, String> o1, Entry<Integer, String> o2) {
                    return o1.getKey().compareTo(o2.getKey());
                }
            })).expectedSize(POPULAR_WORDS_CNT).maximumSize(POPULAR_WORDS_CNT).create();
        }

        /**
         * This method doesn't emit anything, but just keeps track of the top N words.
         *
         * @param key The word.
         * @param vals The words counts.
         * @param ctx Reducer context.
         * @throws IOException If failed.
         * @throws InterruptedException If failed.
         */
        @Override public void reduce(Text key, Iterable<IntWritable> vals, Context ctx) throws IOException,
            InterruptedException {
            int sum = 0;

            for (IntWritable val : vals)
                sum += val.get();

            q.add(immutableEntry(sum, key.toString()));
        }

        /**
         * This method is called after all the word entries have been processed. It writes the accumulated
         * statistics to the job output file.
         *
         * @param ctx The job context.
         * @throws IOException If failed.
         * @throws InterruptedException If failed.
         */
        @Override protected void cleanup(Context ctx) throws IOException, InterruptedException {
            IntWritable i = new IntWritable();

            Text txt = new Text();

            // iterate in desc order
            while (!q.isEmpty()) {
                Entry<Integer, String> e = q.removeFirst();

                i.set(e.getKey());

                txt.set(e.getValue());

                ctx.write(txt, i);
            }
        }
    }

    /**
     * Configures the Hadoop MapReduce job.
     *
     * @return Instance of the Hadoop MapRed job.
     * @throws IOException If failed.
     */
    private Job createConfigBasedHadoopJob() throws IOException {
        Job jobCfg = new Job();

        Configuration cfg = jobCfg.getConfiguration();

        // Use explicit configuration of distributed file system, if provided.
        if (DFS_CFG != null)
            cfg.addResource(U.resolveGridGainUrl(DFS_CFG));

        jobCfg.setJobName("HadoopPopularWordExample");
        jobCfg.setJarByClass(GridHadoopPopularWordsTest.class);
        jobCfg.setInputFormatClass(TextInputFormat.class);
        jobCfg.setOutputKeyClass(Text.class);
        jobCfg.setOutputValueClass(IntWritable.class);
        jobCfg.setMapperClass(TokenizingMapper.class);
        jobCfg.setReducerClass(TopNWordsReducer.class);

        FileInputFormat.setInputPaths(jobCfg, BOOKS_DFS_DIR);
        FileOutputFormat.setOutputPath(jobCfg, RESULT_DFS_DIR);

        // Local job tracker allows the only task per wave, but text input format
        // replaces it with the calculated value based on input split size option.
        if ("local".equals(cfg.get("mapred.job.tracker", "local"))) {
            // Split job into tasks using 32MB split size.
            FileInputFormat.setMinInputSplitSize(jobCfg, 32 * 1024 * 1024);
            FileInputFormat.setMaxInputSplitSize(jobCfg, Long.MAX_VALUE);
        }

        return jobCfg;
    }

    /**
     * Runs the Hadoop job.
     *
     * @return {@code True} if succeeded, {@code false} otherwise.
     * @throws Exception If failed.
     */
    private boolean runWordCountConfigBasedHadoopJob() throws Exception {
        Job job = createConfigBasedHadoopJob();

        // Distributed file system this job will work with.
        FileSystem fs = FileSystem.get(job.getConfiguration());

        X.println(">>> Using distributed file system: " + fs.getHomeDirectory());

        // Prepare input and output job directories.
        prepareDirectories(fs);

        long time = System.currentTimeMillis();

        // Run job.
        boolean res = job.waitForCompletion(true);

        X.println(">>> Job execution time: " + (System.currentTimeMillis() - time) / 1000 + " sec.");

        // Move job results into local file system, so you can view calculated results.
        publishResults(fs);

        return res;
    }

    /**
     * Prepare job's data: cleanup result directories that might have left over
     * after previous runs, copy input files from the local file system into DFS.
     *
     * @param fs Distributed file system to use in job.
     * @throws IOException If failed.
     */
    private void prepareDirectories(FileSystem fs) throws IOException {
        X.println(">>> Cleaning up DFS result directory: " + RESULT_DFS_DIR);

        fs.delete(RESULT_DFS_DIR, true);

        X.println(">>> Cleaning up DFS input directory: " + BOOKS_DFS_DIR);

        fs.delete(BOOKS_DFS_DIR, true);

        X.println(">>> Copy local files into DFS input directory: " + BOOKS_DFS_DIR);

        fs.copyFromLocalFile(BOOKS_LOCAL_DIR, BOOKS_DFS_DIR);
    }

    /**
     * Publish job execution results into local file system, so you can view them.
     *
     * @param fs Distributed file sytem used in job.
     * @throws IOException If failed.
     */
    private void publishResults(FileSystem fs) throws IOException {
        X.println(">>> Cleaning up DFS input directory: " + BOOKS_DFS_DIR);

        fs.delete(BOOKS_DFS_DIR, true);

        X.println(">>> Cleaning up LOCAL result directory: " + RESULT_LOCAL_DIR);

        fs.delete(RESULT_LOCAL_DIR, true);

        X.println(">>> Moving job results into LOCAL result directory: " + RESULT_LOCAL_DIR);

        fs.copyToLocalFile(true, RESULT_DFS_DIR, RESULT_LOCAL_DIR);
    }

    /**
     * Executes a modified version of the Hadoop word count example. Here, in addition to counting the number of
     * occurrences of the word in the source files, the N most popular words are selected.
     *
     * @param args None.
     */
    public static void main(String[] args) {
        try {
            new GridHadoopPopularWordsTest().runWordCountConfigBasedHadoopJob();
        }
        catch (Exception e) {
            X.println(">>> Failed to run word count example: " + e.getMessage());
        }

        System.exit(0);
    }
}
