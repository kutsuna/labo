package com.omotenashimind.labo.neo4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class Main {
	
	public static void main(String[] args) {
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("application-context.xml");
		MaruPekeAI maruPekeAI = (MaruPekeAI)applicationContext.getBean("maruPekeAI");
		
		maruPekeAI.startGame(MaruPekeAI.TURN_FIRST);
	}
}
