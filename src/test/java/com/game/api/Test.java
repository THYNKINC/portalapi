package com.game.api;

import org.apache.commons.math3.distribution.NormalDistribution;

public class Test {

	public static void main(String[] args) {
		
		double cs = 0.833333333333333;
		double is = 0.203703703703704;
		
		double mean = 0;
        double standardDev = 1;
        
        NormalDistribution distribution = new NormalDistribution(mean, standardDev);
        
        double cp1 = distribution.inverseCumulativeProbability(cs);
        double cp2 = distribution.inverseCumulativeProbability(is);
        
		System.out.println(cp1);
		System.out.println(cp2);
		System.out.println(cp1 - cp2);
	}
}
