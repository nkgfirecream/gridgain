/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.rest;

import org.gridgain.client.*;
import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 */
public class GridRestProcessorStartSelfTest extends GridCommonAbstractTest {
    /** */
    private static final GridTcpDiscoveryIpFinder IP_FINDER = new GridTcpDiscoveryVmIpFinder(true);

    /** */
    private static final String HOST = "127.0.0.1";

    /** */
    public static final int TCP_PORT = 11222;

    /** */
    public static final int HTTP_PORT = 8080;

    /** */
    private CountDownLatch gridReady;

    /** */
    private CountDownLatch proceed;

    /** {@inheritDoc}*/
    @Override protected GridConfiguration getConfiguration(String gridName) throws Exception {
        GridConfiguration cfg = super.getConfiguration(gridName);

        cfg.setLocalHost(HOST);
        cfg.setRestTcpPort(TCP_PORT);
        cfg.setRestEnabled(true);

        TestDiscoverySpi disc = new TestDiscoverySpi();

        disc.setIpFinder(IP_FINDER);

        cfg.setDiscoverySpi(disc);

        return cfg;
    }

    /** {@inheritDoc}*/
    @Override protected void beforeTest() throws Exception {
        gridReady = new CountDownLatch(1);
        proceed = new CountDownLatch(1);
    }

    /** {@inheritDoc}*/
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     *  @throws Exception If failed.
     */
    public void testTcpStart() throws Exception {
        GridClientConfiguration clCfg = new GridClientConfiguration();

        clCfg.setProtocol(GridClientProtocol.TCP);
        clCfg.setServers(Collections.singleton(HOST + ":" + TCP_PORT));

        doTest(clCfg);
    }

    /**
     * @throws Exception If failed.
     */
    public void testHttpStart() throws Exception {
        GridClientConfiguration clCfg = new GridClientConfiguration();

        clCfg.setProtocol(GridClientProtocol.HTTP);
        clCfg.setServers(Collections.singleton(HOST + ":" + HTTP_PORT));

        doTest(clCfg);
    }

    /**
     * @param cfg Client configuration.
     * @throws Exception If failed.
     */
    private void doTest(final GridClientConfiguration cfg) throws Exception {
        GridTestUtils.runAsync(new GridCallable<Object>() {
            @Override public Object call() {
                try {
                    startGrid();
                }
                catch (Exception e) {
                    log().error("Grid start failed", e);

                    fail();
                }

                return null;
            }
        });

        try {
            gridReady.await();

            GridFuture<GridClient> c = GridTestUtils.runAsync(new Callable<GridClient>() {
                @Override public GridClient call() throws Exception {
                    return GridClientFactory.start(cfg);
                }
            });

            try {
                proceed.countDown();

                c.get().compute().refreshTopology(false, false);
            }
            finally {
                GridClientFactory.stopAll();
            }
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
        finally {
            proceed.countDown();
        }
    }

    /**
     * Test SPI.
     */
    @GridSpiInfo(author="test", version="test", url="gridgain.com", email="test@gridgain.com")
    private class TestDiscoverySpi extends GridTcpDiscoverySpi {
        /** {@inheritDoc} */
        @Override public void spiStart(@Nullable String gridName) throws GridSpiException {
            gridReady.countDown();

            try {
                proceed.await();
            }
            catch (InterruptedException e) {
                throw new GridSpiException("Failed to await start signal.", e);
            }

            super.spiStart(gridName);
        }
    }
}