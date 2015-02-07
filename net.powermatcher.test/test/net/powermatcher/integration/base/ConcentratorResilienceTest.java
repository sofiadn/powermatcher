package net.powermatcher.integration.base;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.zip.DataFormatException;

import net.powermatcher.api.MatcherEndpoint;
import net.powermatcher.api.data.ArrayBid;
import net.powermatcher.integration.util.ConcentratorWrapper;
import net.powermatcher.integration.util.CsvBidReader;
import net.powermatcher.integration.util.CsvExpectedResultsReader;
import net.powermatcher.mock.MockAgent;
import net.powermatcher.mock.MockMatcherAgent;

import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author FAN
 * @version 2.0
 */
public class ConcentratorResilienceTest
    extends ResilienceTest {

    private static final String MATCHERAGENTNAME = "auctioneer";
    private static final String CONCENTRATOR_NAME = "concentrator";
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcentratorResilienceTest.class);

    // The direct upstream matcher for the agents
    protected ConcentratorWrapper concentrator;

    // mock auctioneer
    protected MockMatcherAgent auctioneer;

    protected void prepareTest(String testID, String suffix) throws IOException, DataFormatException {
        // Create agent list
        agentList = new ArrayList<MockAgent>();

        // Create matcher list
        matchers = new ArrayList<MatcherEndpoint>();

        // Get the expected results
        resultsReader = new CsvExpectedResultsReader(getExpectedResultsFile(testID, suffix));

        marketBasis = resultsReader.getMarketBasis();
        concentrator = new ConcentratorWrapper();
        Map<String, Object> concentratorProperties = new HashMap<String, Object>();
        concentratorProperties.put("matcherId", CONCENTRATOR_NAME);
        concentratorProperties.put("desiredParentId", MATCHERAGENTNAME);
        concentratorProperties.put("bidTimeout", "600");
        concentratorProperties.put("bidUpdateRate", "30");
        concentratorProperties.put("agentId", CONCENTRATOR_NAME);
        concentratorProperties.put("whiteListAgents", new ArrayList<String>());

        matchers.add(concentrator);

        concentrator.setExecutorService(new ScheduledThreadPoolExecutor(10));
        concentrator.setTimeService(new SystemTimeService());
        concentrator.activate(concentratorProperties);

        auctioneer = new MockMatcherAgent(MATCHERAGENTNAME);
        auctioneer.setMarketBasis(marketBasis);
        matchers.add(auctioneer);

        // Session
        sessionManager = new SessionManager();

        sessionManager.addMatcherEndpoint(auctioneer);
        sessionManager.addMatcherEndpoint(concentrator);
        sessionManager.addAgentEndpoint(concentrator);

        sessionManager.activate();

        // Create the bid reader
        bidReader = new CsvBidReader(getBidInputFile(testID, suffix), marketBasis);
    }

    @Override
    protected void sendBidsToMatcher() throws IOException, DataFormatException {

        ArrayBid bid = null;
        MockAgent newAgent;

        double[] aggregatedDemand = new double[marketBasis.getPriceSteps()];

        boolean stop = false;
        int i = 0;
        do {
            try {
                bid = bidReader.nextBid();

                if (bid != null) {
                    // Aggregated demand calculation
                    double[] demand = bid.getDemand();
                    for (int j = 0; j < demand.length; j++) {
                        aggregatedDemand[j] = aggregatedDemand[j] + demand[j];
                    }

                    if (agentList.size() > i) {
                        newAgent = agentList.get(i);
                    } else {
                        newAgent = createAgent(i);
                    }
                    newAgent.sendBid(bid);
                    i++;
                } else {
                    stop = true;
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Incorrect bid specification caught: " + e.getMessage());
                bid = null;
            }
        } while (!stop);
        concentrator.doBidUpdate();
        // Write aggregated demand array
        LOGGER.info("Aggregated demand: ");
        for (int j = 0; j < aggregatedDemand.length; j++) {
            if (j == (aggregatedDemand.length - 1)) {
                LOGGER.info(String.valueOf(aggregatedDemand[j]));
            } else {
                LOGGER.info(String.valueOf((aggregatedDemand[j] + ",")));
            }
        }
    }

    private MockAgent createAgent(int i) {
        String agentId = "agent" + (i + 1);
        MockAgent newAgent = new MockAgent(agentId);
        agentList.add(i, newAgent);

        newAgent.setDesiredParentId(CONCENTRATOR_NAME);
        addAgent(newAgent);
        return newAgent;
    }

    protected void checkEquilibriumPrice() {
        double expPrice = resultsReader.getEquilibriumPrice();

        // Verify the price received by the agents
        for (MockAgent agent : agentList) {
            assertEquals(expPrice, agent.getLastPriceUpdate().getPrice().getPriceValue(), 0);
        }
    }

    @After
    public void tearDown() throws IOException {
        if (bidReader != null) {
            bidReader.closeFile();
        }
        removeAgents(agentList, concentrator);
    }
}