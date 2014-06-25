/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.hadoop;

import org.gridgain.grid.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Hadoop job.
 */
public interface GridHadoopJob {
    /**
     * Gets job ID.
     *
     * @return Job ID.
     */
    public GridHadoopJobId id();

    /**
     * Gets job information.
     *
     * @return Job information.
     */
    public GridHadoopJobInfo info();

    /**
     * Gets collection of input splits for this job.
     *
     * @return Input splits.
     */
    public Collection<GridHadoopInputSplit> input() throws GridException;

    /**
     * Gets partitioner for the job.
     *
     * @return Partitioner.
     */
    public GridHadoopPartitioner partitioner() throws GridException;

    /**
     * Creates new instance of key serialization object.
     *
     * @return Serialization facility.
     * @throws GridException if failed.
     */
    public GridHadoopSerialization keySerialization() throws GridException;

    /**
     * Creates new instance of value serialization object.
     *
     * @return Serialization facility.
     * @throws GridException if failed.
     */
    public GridHadoopSerialization valueSerialization() throws GridException;

    /**
     * Creates mapper output key sorting comparator.
     *
     * @return New sort comparator.
     */
    public Comparator<?> sortComparator();

    /**
     * Creates reducer key grouping comparator.
     *
     * @return New group comparator.
     */
    @Nullable public Comparator<?> reduceGroupComparator();

    /**
     * Creates combiner key grouping comparator.
     *
     * @return New group comparator.
     */
    @Nullable public Comparator<?> combineGroupComparator();

    /**
     * Creates task to be executed.
     *
     * @param taskInfo Task info.
     * @return Task.
     */
    public GridHadoopTask createTask(GridHadoopTaskInfo taskInfo);

    /**
     * Does all the needed initialization for the job. Will be called on each node where tasks for this job must
     * be executed.
     * <p>
     * If job is running in external mode this method will be called on instance in GridGain node with parameter
     * {@code false} and on instance in external process with parameter {@code true}.
     *
     * @param external If {@code true} then this job instance resides in external process.
     * @param locNodeId Local node ID.
     * @throws GridException If failed.
     */
    public void initialize(boolean external, UUID locNodeId) throws GridException;

    /**
     * Release all the resources.
     * <p>
     * If job is running in external mode this method will be called on instance in GridGain node with parameter
     * {@code false} and on instance in external process with parameter {@code true}.
     *
     * @param external If {@code true} then this job instance resides in external process.
     * @throws GridException If failed.
     */
    public void dispose(boolean external) throws GridException;
}