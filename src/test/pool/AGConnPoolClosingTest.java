/******************************************************************************
** Copyright (c) 2008-2013 Franz Inc.
** All rights reserved. This program and the accompanying materials
** are made available under the terms of the Eclipse Public License v1.0
** which accompanies this distribution, and is available at
** http://www.eclipse.org/legal/epl-v10.html
******************************************************************************/

package test.pool;

import static test.Util.closeWait;
import static test.Util.netstat;
import static test.util.Clj.applyStr;
import static test.util.Clj.interpose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import test.AGAbstractTest;
import test.TestSuites;
import test.Util;
import test.util.Clj;

import clojure.lang.AFn;

import com.franz.agraph.pool.AGConnPool;
import com.franz.agraph.pool.AGConnProp;
import com.franz.agraph.pool.AGPoolProp;
import com.franz.agraph.repository.AGCatalog;
import com.franz.agraph.repository.AGRepository;
import com.franz.agraph.repository.AGRepositoryConnection;
import com.franz.agraph.repository.AGServer;
import com.franz.util.Closer;

public class AGConnPoolClosingTest extends Closer {

	private final Logger log = LoggerFactory.getLogger(this.getClass());

    @After
    public void closeAfter() throws Exception {
    	close();
    	Map<String, String> sessions = AGAbstractTest.sessions(AGAbstractTest.newAGServer());
    	if (!sessions.isEmpty()) {
        	log.warn("sessions after close: " + sessions);
    	}
    }

    @Test
    @Category(TestSuites.Stress.class)
    public void openAG() throws Exception {
    	final String repoName = "pool.openAG";
		final AGConnPool pool = closeLater( AGConnPool.create(
				AGConnProp.serverUrl, AGAbstractTest.findServerUrl(),
				AGConnProp.username, AGAbstractTest.username(),
				AGConnProp.password, AGAbstractTest.password(),
				AGConnProp.catalog, AGAbstractTest.CATALOG_ID,
				AGConnProp.repository, repoName,
				AGConnProp.session, AGConnProp.Session.DEDICATED,
				AGConnProp.sessionLifetime, TimeUnit.MINUTES.toSeconds(5),
				AGPoolProp.shutdownHook, true,
				AGPoolProp.initialSize, 2,
				AGPoolProp.maxActive, 6,
				AGPoolProp.maxWait, TimeUnit.SECONDS.toMillis(45),
				AGPoolProp.maxIdle, 8
				// AGPoolProp.minIdle, 2,
				// AGPoolProp.timeBetweenEvictionRunsMillis, TimeUnit.MINUTES.toMillis(5),
				// AGPoolProp.testWhileIdle, true
		));
		
	Assert.assertEquals(pool.toString(), 0, pool.getNumActive());

	final int NUM = 10; // Number of workers
	final int HOLD_TIME = 20; // seconds

	final AGServer server = AGAbstractTest.newAGServer();
	AGCatalog catalog = server.getCatalog(AGAbstractTest.CATALOG_ID);
	final AGRepository repo = closeLater(catalog.createRepository(repoName));
	repo.setConnPool(pool);

        ExecutorService exec = Executors.newFixedThreadPool(NUM);
        final List<Throwable> errors = new ArrayList<Throwable>();
        for (int i = 0; i < NUM; i++) {
			exec.execute(new Runnable() {
				public void run() {
					try {
						AGRepositoryConnection conn = repo.getConnection();
						try {
							conn.size();
							// Hold the connection for a while 
							Thread.sleep(TimeUnit.SECONDS.toMillis(HOLD_TIME));
							// Make sure it still works.
							conn.size();
						} finally {
						    // Now return it to the pool.
							conn.close();
						}
					} catch (Throwable e) {
						errors.add(e);
					}
				}
			});
		}
	/* Given the current configuration, 6 workers will be able to acquire a connection
	   object.  The remaining 4 will block.  The original 6 will (in parallel)
	   delay for HOLD_TIME seconds, return their connection to the pool, then terminate.
	   The remaining 4 will them able to proceed.  So ultimately we will have two sets
	   of delays, so that's how long we need to wait (plus some fudge to compensate for unexpected
	   delays) */
	final int WAIT_FUDGE = 5;
        exec.awaitTermination(HOLD_TIME * 2 + WAIT_FUDGE, TimeUnit.SECONDS);
        Assert.assertEquals(pool.toString(), 0, pool.getNumActive());
        if (!errors.isEmpty()) {
        	for (Throwable e : errors) {
				log.error("error", e);
			}
        	Assert.fail("see log for details: " + errors.toString());
        }

        close();
    	long start = System.nanoTime();
        Map<String, String> sessions = Util.waitForSessions(server, repoName);
        Assert.assertNull("Sessions alive " + TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - start)) + " seconds after closing: " + sessions, sessions);
        close(server);
    }
    
	private static List<String> netstatLinesToRegex(List<String> netstatBefore) throws Exception {
		return Clj.map(new AFn() {
        	public Object invoke(Object netstatLine) {
        		String[] netstatFields = ((String)netstatLine).split(" +");
        		return ".*" + netstatFields[3] + " +" + netstatFields[4] + ".*";
        	}
		}, netstatBefore);
	}
	
    @Test
    @Category(TestSuites.Stress.class)
    public void openSockets_bug21099() throws Exception {
    	final String repoName = "pool.bug21099";
    	
        Thread.sleep(1000);
        List<String> netstatBefore = netstat();
        {
        	log.info("openSockets_bug21099 netstatBefore:\n" + applyStr(interpose("\n", netstatBefore)));
                List<String> closeWait = closeWait(netstatBefore);
                if (!closeWait.isEmpty()) {
                        log.warn("openSockets_bug21099 netstat close_wait Before:\n" + applyStr(interpose("\n", closeWait)));
                }
        }
        // use a regex to get the ports from netstat, but allow for the state to change (when filtering in waitForNetStat below)
        netstatBefore = netstatLinesToRegex(netstatBefore);
        log.info("openSockets_bug21099 netstatBefore regexes:\n" + applyStr(interpose("\n", netstatBefore)));
        final int maxIdle = 20;
    	AGConnPool pool = closeLater( AGConnPool.create(
                                              AGConnProp.serverUrl, AGAbstractTest.findServerUrl(),
                                              AGConnProp.username, AGAbstractTest.username(),
                                              AGConnProp.password, AGAbstractTest.password(),
                                              AGConnProp.catalog, AGAbstractTest.CATALOG_ID,
                                              AGConnProp.repository, repoName,
                                              AGConnProp.session, AGConnProp.Session.TX,
                AGPoolProp.maxActive, 40,
                AGPoolProp.maxWait, 40000,
                AGPoolProp.shutdownHook, true,
                AGPoolProp.testOnBorrow, false,
                AGPoolProp.minIdle, 10,
                AGPoolProp.maxIdle, maxIdle,
                AGPoolProp.timeBetweenEvictionRunsMillis, 1000,
                AGPoolProp.minEvictableIdleTimeMillis, 2000,
                AGConnProp.sessionLifetime, 30, // seconds
                AGPoolProp.testWhileIdle, true,
                AGPoolProp.numTestsPerEvictionRun, 5,
                AGPoolProp.initialSize, 5
    	));

	/* The AGConnPool configuration above will start off with 5
	   (initialSize) session connections, then will immediately
	   jump up to 10 (minIdle).  Then, every second, connections
	   that have lived more than two seconds will be closed and
	   new session connections will be created to replace them.
           
	   We let this cycle operate for a while (30 seconds).
	   Afterward, we verify that we haven't leaked any session
	   connections.  We use the presence of sockets in the
	   CLOSE_WAIT state to determine if we've had such a leak.  As
	   a reminder, a socket in CLOSE_WAIT state means that the
	   remote end of the socket has closed but the local end
	   hasn't yet.  So, the leak detection depends on the
	   dedication session exceeding its sessionLifetime and
	   closing down. 
	*/

        Thread.sleep(30000);

        List<String> netstat = Util.waitForNetStat(0, netstatBefore);
        List<String> closeWait = closeWait(netstat);
        Assert.assertTrue("sockets in CLOSE_WAIT:\n" + applyStr(interpose("\n", closeWait)), closeWait.isEmpty());
        // there may be maxIdle sockets "ESTABLISHED" and some in TIME_WAIT, so check for (maxIdle*2)
        Assert.assertTrue("too many sockets open:\n" + applyStr(interpose("\n", netstat)), (maxIdle*2) >= netstat.size());

        close(pool);

        netstat = Util.waitForNetStat(120, netstatBefore);
        Assert.assertNull("sockets open after closing pool:\n" + applyStr(interpose("\n", netstat)), netstat);

        final AGServer server = closeLater(AGAbstractTest.newAGServer());
        long start = System.nanoTime();
        Map<String, String> sessions = Util.waitForSessions(server, repoName);
        Assert.assertNull("Sessions alive " + TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - start)) + " seconds after closing.", sessions);
        close(server);
    }

    @Test
    @Category(TestSuites.Broken.class)
    /**
     * Test without the pool.
     */
    public void openSockets_bug21109_direct() throws Exception {
        Thread.sleep(1000);
        List<String> netstatBefore = netstat();
    	log.info("openSockets_bug21109_direct netstatBefore:\n" + applyStr(interpose("\n", netstatBefore)));
        // use a regex to get the ports from netstat, but allow for the state to change (when filtering in waitForNetStat below)
        netstatBefore = netstatLinesToRegex(netstatBefore);
        
    	final String repoName = "direct.bug21109";
    	AGServer server = closeLater(AGAbstractTest.newAGServer());
    	AGCatalog cat = server.getCatalog(AGAbstractTest.CATALOG_ID);
    	AGRepository repo = cat.createRepository(repoName);
    	for (int i = 0; i < 30; i++) {
        	AGRepositoryConnection conn1 = closeLater(repo.getConnection());
        	conn1.setAutoCommit(false);
        	AGRepositoryConnection conn2 = closeLater(repo.getConnection());
        	conn2.setAutoCommit(false);
        	AGRepositoryConnection conn3 = closeLater(repo.getConnection());
        	conn3.setAutoCommit(false);
        	close(conn1);
        	close(conn2);
        	close(conn3);
        	if (i==5) {
                        log.debug("netstat " + i + ":\n" + applyStr(interpose("\n", netstat())));
        	}
        }
        List<String> netstat = Util.waitForNetStat(0, netstatBefore);
        List<String> closeWait = closeWait(netstat);
        Assert.assertTrue("sockets in CLOSE_WAIT:\n" + applyStr(interpose("\n", closeWait)), closeWait.isEmpty());
        Assert.assertTrue("too many sockets open:\n" + applyStr(interpose("\n", netstat)), 10 >= netstat.size());
        
        repo = close(repo);
        server = close(server);
        
        netstat = Util.waitForNetStat(120, netstatBefore);
        Assert.assertNull("sockets open after closing pool:\n" + applyStr(interpose("\n", netstat)), netstat);
        
    	server = closeLater(AGAbstractTest.newAGServer());
    	long start = System.nanoTime();
        Map<String, String> sessions = Util.waitForSessions(server, repoName);
        Assert.assertNull("Sessions alive " + TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - start)) + " seconds after closing: " + sessions, sessions);
        close(server);
    }

}
