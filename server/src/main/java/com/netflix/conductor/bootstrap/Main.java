/**
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.grpc.server.GRPCServerProvider;
import com.netflix.conductor.jetty.server.JettyServerProvider;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Viren Entry point for the server
 */
public class Main {

    private static final int EMBEDDED_ES_INIT_TIME = 5000;

//    public static Producer<String, String> createProducer() {
//        Properties props = new Properties();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.97.18.46:9092");
//        props.put(ProducerConfig.CLIENT_ID_CONFIG, "NetflixConductorProducer");
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
//        //props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, CustomPartitioner.class.getName());
//        return new KafkaProducer<>(props);
//    }

    public static void main(String[] args) throws Exception {
//        final Producer<String, String> producer = createProducer();
//        long time = System.currentTimeMillis();
//
//        try {
//            final ProducerRecord<String, String> record = new ProducerRecord<>("test", "" + time, "Hello Mom " + time);
//            RecordMetadata metadata = producer.send(record).get();
//
//            long elapsedTime = System.currentTimeMillis() - time;
//            System.out.printf("sent record(key=%s value=%s) meta(partition=%d, offset=%d) time=%d\n",
//                    record.key(), record.value(), metadata.partition(), metadata.offset(), elapsedTime);
//        } finally {
//            producer.flush();
//            producer.close();
//        }

        loadConfigFile(args.length > 0 ? args[0] : System.getenv("CONDUCTOR_CONFIG_FILE"));

        if (args.length == 2) {
            System.out.println("Using log4j config " + args[1]);
            PropertyConfigurator.configure(new FileInputStream(new File(args[1])));
        }

        Injector bootstrapInjector = Guice.createInjector(new BootstrapModule());
        ModulesProvider modulesProvider = bootstrapInjector.getInstance(ModulesProvider.class);
        Injector serverInjector = Guice.createInjector(modulesProvider.get());

        Optional<EmbeddedElasticSearch> embeddedSearchInstance = serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get();
        if (embeddedSearchInstance.isPresent()) {
            try {
                embeddedSearchInstance.get().start();
                /*
                 * Elasticsearch embedded instance does not notify when it is up and ready to accept incoming requests.
                 * A possible solution for reading and writing into the index is to wait a specific amount of time.
                 */
                Thread.sleep(EMBEDDED_ES_INIT_TIME);
            } catch (Exception ioe) {
                ioe.printStackTrace(System.err);
                System.exit(3);
            }
        }

        try {
            serverInjector.getInstance(IndexDAO.class).setup();
        } catch (Exception e){
            e.printStackTrace(System.err);
            System.exit(3);
        }


        System.out.println("\n\n\n");
        System.out.println("                     _            _             ");
        System.out.println("  ___ ___  _ __   __| |_   _  ___| |_ ___  _ __ ");
        System.out.println(" / __/ _ \\| '_ \\ / _` | | | |/ __| __/ _ \\| '__|");
        System.out.println("| (_| (_) | | | | (_| | |_| | (__| || (_) | |   ");
        System.out.println(" \\___\\___/|_| |_|\\__,_|\\__,_|\\___|\\__\\___/|_|   ");
        System.out.println("\n\n\n");

        serverInjector.getInstance(GRPCServerProvider.class).get().ifPresent(server -> {
            try {
                server.start();
            } catch (IOException ioe) {
                ioe.printStackTrace(System.err);
                System.exit(3);
            }
        });

        serverInjector.getInstance(JettyServerProvider.class).get().ifPresent(server -> {
            try {
                server.start();
            } catch (Exception ioe) {
                ioe.printStackTrace(System.err);
                System.exit(3);
            }
        });

    }

    private static void loadConfigFile(String propertyFile) throws IOException {
        if (propertyFile == null) return;
        System.out.println("Using config file: " + propertyFile);
        Properties props = new Properties(System.getProperties());
        props.load(new FileInputStream(propertyFile));
        System.setProperties(props);
    }
}
