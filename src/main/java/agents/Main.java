package agents;

import agents.multitool.MultiToolAgent;
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Google ADK Multi-Tool Agent");
        System.out.println("This agent can provide information about time and weather for various cities.");
        System.out.println("---------------------------------------------------------------------");

        MultiToolAgent.main(args);
    }
}