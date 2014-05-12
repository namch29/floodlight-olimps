package net.floodlightcontroller.flowcache;

import static org.easymock.EasyMock.createMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.flowcache.FlowCache;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.IFlowQueryHandler;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.IOlimpsTopologyService;

import org.junit.Before;
import org.junit.Test;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

public class FlowCacheTest extends FloodlightTestCase {
	/** The flow cache to test. */
	FlowCache flowCache = new FlowCache();
	/** The flow cache service to test. */
	IFlowCacheService flowCacheService;
	/** The Floodlight context. */
	FloodlightContext cntx;
	/** */
	FloodlightModuleContext fmc;
	/** */
    ILinkDiscoveryService lds;
    /** */
    MockThreadPoolService tps;
    /** */
    DefaultEntityClassifier entityClassifier;
    /** */
    IOlimpsTopologyService topology;
	

	/** A standard switch id. */
	long switchId_1 = 1L;
	long switchId_2 = 2L;
	/** A standard cookie. */
	long cookie = 1L;
	/** A standard priority. */
	int priority_1 = 1;
	int priority_2 = 1;
	/** Wildcards, generated by the matching fields, i.e. the wildcards are the inverse. */
	int wildcards = (Wildcards.ofMatches(Flag.DL_TYPE, Flag.NW_SRC, Flag.NW_DST, Flag.NW_PROTO, Flag.TP_SRC, Flag.TP_DST)).getInt();
	/** OpenFlow matches.*/
	OFMatch match_1 = new OFMatch()
		.setDataLayerType((short) 1)
		.setNetworkSource(1)
		.setNetworkDestination(2)
		.setNetworkProtocol((byte) 1)
		.setTransportSource((short) 1)
		.setTransportDestination((short) 2)
		.setWildcards(wildcards);
	OFMatch match_2 = new OFMatch()
		.setDataLayerType((short) 1)
		.setNetworkSource(2)
		.setNetworkDestination(1)
		.setNetworkProtocol((byte) 1)
		.setTransportSource((short) 2)
		.setTransportDestination((short) 1)
		.setWildcards(wildcards);
	/** Port used by the output actions. */
	short port_1 = 2;
	short port_2 = 1;
	/** Output actions used by the flow cache objects. */
	OFActionOutput action_1 = new OFActionOutput()
		.setPort((short) port_1);
	OFActionOutput action_2 = new OFActionOutput()
		.setPort((short) port_2);
	/** List of output actions used by the flow cache objects. */
	List<OFAction> actionList_1 = new ArrayList<OFAction>(Arrays.asList(action_1));
	List<OFAction> actionList_2 = new ArrayList<OFAction>(Arrays.asList(action_2));
	List<OFAction> actionList_3 = new ArrayList<OFAction>(Arrays.asList(action_1, action_2));
	
	/** A standard flow cache object, as installed e.g. by the forwarding application. */
	FlowCacheObj fco_1 = new FlowCacheObj(cookie, priority_1, match_1, actionList_1);
	/** A standard flow cache object, as installed e.g. by the forwarding application. */
	FlowCacheObj fco_2 = new FlowCacheObj(cookie, priority_1, match_2, actionList_2);
	/** A flow cache object, with the same match, but different action as fco_1. */
	FlowCacheObj fco_3 = new FlowCacheObj(cookie, priority_1, match_1, actionList_2);
	/** A flow cache object, similar (i.e. same hash) to fco_1, as generated by a flow remove message. */
	FlowCacheObj fco_4 = new FlowCacheObj(cookie, priority_1, match_1, null);
	/** A flow cache object, similar (i.e. same hash) to fco_2, as generated by a flow remove message. */
	FlowCacheObj fco_5 = new FlowCacheObj(cookie, priority_1, match_2, null);
	/** A flow cache object, as generated by a flow remove message. */
	FlowCacheObj fco_6 = new FlowCacheObj(cookie, priority_1, match_1, actionList_3);
	
	/**
	 * Just a dummy flow query handler that does nothing.
	 */
	protected class dummyQueryHandler implements IFlowQueryHandler {
		@Override
		public void flowQueryRespHandler(FlowCacheQueryResp resp) {
			// NO-OP
		}
	}
	
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		cntx = new FloodlightContext();
		mockFloodlightProvider = getMockFloodlightProvider();
		tps = new MockThreadPoolService();
		entityClassifier = new DefaultEntityClassifier();
		lds = createMock(ILinkDiscoveryService.class);
		topology = createMock(IOlimpsTopologyService.class);
        fmc = new FloodlightModuleContext();
		
		FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IThreadPoolService.class, tps);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        fmc.addService(IOlimpsTopologyService.class, topology);
        fmc.addService(ILinkDiscoveryService.class, lds);
        fmc.addService(IFlowCacheService.class, flowCache);

        tps.init(fmc);
        entityClassifier.init(fmc);
        getMockFloodlightProvider().init(fmc);
        flowCache.init(fmc);

        tps.startUp(fmc);
        entityClassifier.startUp(fmc);
        getMockFloodlightProvider().startUp(fmc);
        flowCache.startUp(fmc);
        
        flowCacheService = fmc.getServiceImpl(IFlowCacheService.class);
	}

	@Test
	public void testPathIdMultipleSwitchQueryDB() {
		// The path Id to query for.
		int pathId = 1;
		// Populate database.
		FlowCacheObj stored_1 = this.flowCacheService.addFlow(FlowCache.DEFAULT_DB_NAME, switchId_1, cookie, (short) priority_1, match_1, actionList_1);
		if (stored_1 == null) {
			fail("Was not able to store the entry fco_1.");
		} else {
			stored_1.setPathId(pathId);
		}
		FlowCacheObj stored_2 = this.flowCacheService.addFlow(FlowCache.DEFAULT_DB_NAME, switchId_2, cookie, (short) priority_1, match_1, actionList_2);
		if (stored_2 == null) {
			fail("Was not able to store the entry fco_1.");
		} else {
			stored_2.setPathId(pathId);
		}
		
		// Create flow cache query.
		FlowCacheQuery fcq = new FlowCacheQuery(new dummyQueryHandler(), IFlowCacheService.DEFAULT_DB_NAME, "test", null, switchId_1)
			.setPathId(pathId);
		Future<FlowCacheQueryResp> future = this.flowCacheService.queryDB(fcq);
		FlowCacheQueryResp fcqr = null;
		try {
			fcqr = future.get(10, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			fail(e.toString());
		}
		
		if (fcqr == null) {
			fail("Flow query result is null");
		}
		
		if (fcqr.flowCacheObjList == null) {
			fail("Flow query result is null");
		}

		if (fcqr.flowCacheObjList.size() != 1) {
			fail("Resulting query map size does not equal 1: " + fcqr);
		}
		
		FlowCacheObj fco = fcqr.flowCacheObjList.get(0);
		if (fco.getPathId() != pathId) {
			fail("The path Id is wrong.");
		}
		
		assert(true);
	}
	
}
