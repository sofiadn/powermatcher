package net.powermatcher.core.concentrator;

import java.util.Deque;
import java.util.LinkedList;

import net.powermatcher.api.messages.BidUpdate;
import net.powermatcher.core.bidcache.AggregatedBid;

/**
 * This store keeps track of send bids to be able to retrieve them later
 * 
 * @author FAN
 * @version 2.0
 * 
 */
public class BidHistoryStore {
    private static final int MAX_BIDS = 900;

    private final Deque<SentBidInformation> sentBids = new LinkedList<SentBidInformation>();

    public void saveBid(final AggregatedBid aggregatedBid, final BidUpdate sentBidUpdate) {
        SentBidInformation info = new SentBidInformation(aggregatedBid, sentBidUpdate);

        synchronized (sentBids) {
            sentBids.add(info);

            if (sentBids.size() > MAX_BIDS) {
                while (sentBids.size() > MAX_BIDS) {
                    sentBids.removeFirst();
                }
            }
        }
    }

    public SentBidInformation retrieveAggregatedBid(int bidNumberReference) {
        synchronized (sentBids) {
            // First check if we have actually sent a bid with that number
            boolean found = false;
            for (SentBidInformation info : sentBids) {
                if (info.getBidNumber() == bidNumberReference) {
                    found = true;
                }
            }

            // If we haven't, then throw an exception
            if (!found) {
                throw new IllegalArgumentException("No bid with bidNumber " + bidNumberReference + " is available");
            }

            // If we have, drop all older bids and return the found info
            SentBidInformation info = sentBids.peek();
            while (info.getBidNumber() != bidNumberReference) {
                sentBids.removeFirst();
                info = sentBids.peek();
            }
            return info;
        }
    }
}
