package fr.unice.polytech.al.trafficlight.crossroad;

import fr.unice.polytech.al.trafficlight.utils.RuleGroup;
import fr.unice.polytech.al.trafficlight.utils.Scenario;
import org.apache.log4j.Logger;

/**
 * Created by nathael on 27/10/16.
 */
class CrossroadModuleRunning implements Runnable {
    private final static Logger LOG = Logger.getLogger(CrossroadModuleRunning.class);
    private final CrossroadModuleCore crossModuleCore;
    private volatile Scenario activeScenario;
    private volatile boolean isRunning = false;

    CrossroadModuleRunning(final CrossroadModuleCore crossroadModuleCore) {
        this.crossModuleCore = crossroadModuleCore;
    }


    Scenario getActiveScenario() {
        return activeScenario;
    }

    void changeScenario(final Scenario newScenario) {
        if(isRunning == false) {
            this.activeScenario = newScenario;
            startRunning();
        }
        else {
            this.activeScenario = newScenario;
            // TODO : should be adapted to active scenario to not make people crazy
        }
        LOG.debug("Scenario set to "+ newScenario);
    }

    void stopRunning() {
        isRunning = false;
        LOG.debug("Running set to FALSE");
    }

    private void startRunning() {
        if(isRunning)
            throw new RuntimeException("Already running !");

        LOG.info("New CrossRoadModuleRunning started");
        isRunning = true;
        new Thread(this).start();
    }
    boolean isRunning() {
        return isRunning;
    }

    @Override
    public void run() {
        try {
            int currentRule = 0;
            while(isRunning()) {
                currentRule++;
                RuleGroup activeRule;
                try {
                    activeRule = activeScenario.getRuleGroup(currentRule);
                    if (activeRule == null) {
                        currentRule = 0;
                        activeRule = activeScenario.getRuleGroup(0);
                    }
                } catch(IndexOutOfBoundsException ioobe) {
                    // activeScenario has no groupRules
                    stopRunning();
                    return; // so die.
                }
                LOG.debug("All Red "+activeScenario.getTransitionTime()+"s");
                // set all traffic lights to red
                crossModuleCore.getTrafficLights().forEach(TrafficLight::setRed);

                // wait for transition
                Thread.sleep(activeScenario.getTransitionTime()*1000);


                LOG.debug("Running step:"+currentRule+" "+activeRule.getGreenTime()+"s ("+activeRule+")");

                // set to green all traffic lights specified in rule
                final RuleGroup finalActiveRule = activeRule;
                crossModuleCore.getTrafficLights().forEach(trafficLight -> {
                    if(finalActiveRule.getTrafficLights().contains(trafficLight.getId()))
                        trafficLight.setGreen();
                    else
                        LOG.debug(finalActiveRule.getTrafficLights()+" does not contains "+trafficLight);
                });

                // wait for step
                Thread.sleep(activeRule.getGreenTime()*1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            stopRunning();
        }
    }
}