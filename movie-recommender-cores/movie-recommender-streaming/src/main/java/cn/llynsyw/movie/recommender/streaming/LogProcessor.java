package cn.llynsyw.movie.recommender.streaming;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.TopologyBuilder;

import java.util.Properties;

/**
 * @ClassName: LogProcessor
 * @Description:
 * @Author: wushengran on 2019/4/4 10:49
 * @Version: 1.0
 */
public class LogProcessor implements Processor<byte[], byte[]> {

    private ProcessorContext context;

    @Override
    public void init(ProcessorContext processorContext) {
        this.context = processorContext;
    }

    @Override
    public void process(byte[] dummy, byte[] line) {
        // 把收集到的日志信息用string表示
        String input = new String(line);
        // 根据前缀MOVIE_RATING_PREFIX:从日志信息中提取评分数据
        if (input.contains("MOVIE_RATING_PREFIX:")) {
            System.out.println("movie rating data coming!>>>>>>>>>>>" + input);

            input = input.split("MOVIE_RATING_PREFIX:")[1].trim();
            context.forward("logProcessor".getBytes(), input.getBytes());
        }
    }

    @Override
    public void punctuate(long l) {

    }

    @Override
    public void close() {

    }

    public void startProcess() {
        String brokers = "linux:9092";
        String zookeepers = "linux:2181";

        // 输入和输出的topic
        String from = "log";
        String to = "recommender";

        // 定义kafka streaming的配置
        Properties settings = new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "logFilter");
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        settings.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, zookeepers);

        // 创建 kafka stream 配置对象
        StreamsConfig config = new StreamsConfig(settings);

        // 创建一个拓扑建构器
        TopologyBuilder builder = new TopologyBuilder();

        // 定义流处理的拓扑结构
        builder.addSource("SOURCE", from)
                .addProcessor("PROCESSOR", LogProcessor::new, "SOURCE")
                .addSink("SINK", to, "PROCESSOR");

        KafkaStreams streams = new KafkaStreams(builder, config);

        streams.start();

        System.out.println("LogProcessor started!>>>>>>>>>>>");
    }

}
