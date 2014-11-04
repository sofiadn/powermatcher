package net.powermatcher.examples;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.powermatcher.api.AgentRole;
import net.powermatcher.api.Session;
import net.powermatcher.api.TimeService;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.Price;
import net.powermatcher.api.data.PricePoint;
import net.powermatcher.api.monitoring.IncomingPriceUpdateEvent;
import net.powermatcher.api.monitoring.Observable;
import net.powermatcher.api.monitoring.Observer;
import net.powermatcher.api.monitoring.OutgoingBidUpdateEvent;
import net.powermatcher.api.monitoring.UpdateEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = PVPanelAgent.Config.class, immediate = true)
public class PVPanelAgent implements AgentRole, Observable {
	private static final Logger logger = LoggerFactory.getLogger(PVPanelAgent.class);
	
	public static interface Config {
		@Meta.AD(deflt = "(matcherId=concentrator)")
		String matcherRole_target();

		@Meta.AD(deflt = "pvpanel")
		String agentId();
		
		@Meta.AD(deflt = "30", description = "Number of seconds between bid updates")
		long bidUpdateRate();
	}

	private ScheduledFuture<?> scheduledFuture;
	private ScheduledExecutorService scheduler;

	@Reference
	public void setScheduler(ScheduledExecutorService scheduler) {
		this.scheduler = scheduler;
	}

	private TimeService timeService;
	private String agentId;

	@Reference
	public void setTimeService(TimeService timeService) {
		this.timeService = timeService;
	}

	@Activate
	public void activate(Map<String, Object> properties) {
		Config config = Configurable.createConfigurable(Config.class, properties);
		agentId = config.agentId();

		scheduledFuture = scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				doBidUpdate();
			}
		}, 0, config.bidUpdateRate(), TimeUnit.SECONDS);

		logger.info("Agent [{}], activated and connected to [{}]", config.agentId(), config.matcherRole_target());
	}

	protected void doBidUpdate() {
		if (session != null) {
			Bid newBid = new Bid(session.getMarketBasis(), new PricePoint(0,
					-700));
			logger.debug("updateBid({})", newBid);
			session.updateBid(newBid);
			publishEvent(new OutgoingBidUpdateEvent("agentId",
					session.getSessionId(), timeService.currentDate(), newBid));
		}
	}

	@Deactivate
	public void deactivate() {
		if(session != null) {
			session.disconnect();
		}
		
		scheduledFuture.cancel(false);
		
		logger.info("Agent [{}], deactivated", agentId);
	}

	private Session session;

	@Override
	public void connect(Session session) {
		this.session = session;
	}
	
	@Override
	public void disconnect(Session session) {
		this.session = null;
	}

	@Override
	public void updatePrice(Price newPrice) {
		logger.debug("updatePrice({})", newPrice);
		// TODO real arguments
		publishEvent(new IncomingPriceUpdateEvent("agentId",
				session.getSessionId(), timeService.currentDate(), newPrice));

		logger.debug("Received price update [{}]", newPrice);
	}

	// TODO refactor to separate (base)object
	private final Set<Observer> observers = new CopyOnWriteArraySet<Observer>();

	@Override
	public void addObserver(Observer observer) {
		observers.add(observer);
	}

	@Override
	public void removeObserver(Observer observer) {
		observers.remove(observer);
	}

	void publishEvent(UpdateEvent event) {
		for (Observer observer : observers) {
			observer.update(event);
		}
	}
}