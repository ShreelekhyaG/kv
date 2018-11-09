package com.stackroute;

import com.stackroute.algos.POSTagging;
import com.stackroute.communicators.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootApplication
public class ParagraphProcessorApplication implements ApplicationListener<ContextRefreshedEvent> {

	@Autowired
	private KafkaTemplate<String,String> kafkaTemplate;

	private static KafkaProducer producer;
	private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphProcessorApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ParagraphProcessorApplication.class, args);
		POSTagging pst = new POSTagging();
		pst.getFullTextSearch().indexer();
		String response = pst.getFullTextSearch().search("narcolepsy");

		LOGGER.info("data generated so far:\n {}",response);
		producer.post("some dummy message produced...");
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
		producer = new KafkaProducer(this.kafkaTemplate);
	}
}
