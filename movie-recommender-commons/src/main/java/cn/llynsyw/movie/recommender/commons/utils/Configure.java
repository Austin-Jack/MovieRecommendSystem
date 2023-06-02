package cn.llynsyw.movie.recommender.commons.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static cn.llynsyw.movie.recommender.commons.constant.Constant.*;

/**
 * 配置加载器
 *
 * @author luolinyuan
 * @since 2023/5/12
 **/
public class Configure {
    private static final Map<String, String> DEFAULT_CONFIG_MAP;
    private Map<String, String> specificConfigMap;

    public static Map<String, String> getDefaultConfigMap() {
        return DEFAULT_CONFIG_MAP;
    }

    private Configure() {
    }

    private Configure(String configFilePath) {
        try (InputStream inputStream =
                     new FileInputStream(configFilePath)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            specificConfigMap = new HashMap<>(properties.size());
            properties.forEach((key, value) -> specificConfigMap.put((String) key, (String) value));
        } catch (IOException e) {
            throw new RuntimeException("loading " + configFilePath + " error");
        }
    }

    static {
        try (InputStream inputStream =
                     Configure.class.getClassLoader().getResourceAsStream("recommender-default" + ".properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            DEFAULT_CONFIG_MAP = new HashMap<>(properties.size());
            properties.forEach((key, value) -> DEFAULT_CONFIG_MAP.put((String) key, (String) value));
        } catch (IOException e) {
            throw new RuntimeException("loading recommender-default.properties error");
        }
    }

    public String getOrDefault(String configName) {
        return specificConfigMap.getOrDefault(configName, DEFAULT_CONFIG_MAP.get(configName));
    }

    public static class Builder {
        private Configure configure;

        private Builder() {
            this.configure = new Configure();
        }

        private Builder datasetPathPrefix(String pathPrefix) {
            this.configure.specificConfigMap.put(CONFIG_KEY_FOR_DATASET_PATH_PREFIX, pathPrefix);
            return this;
        }



        public Configure build() {
            return this.configure;
        }

    }
}
