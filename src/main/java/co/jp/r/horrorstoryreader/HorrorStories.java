package co.jp.r.horrorstoryreader;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "horror")
public class HorrorStories {

    List<String> stories = new ArrayList<>();

    public List<String> getStories() {
        return stories;
    }

    public void setStories(List<String> stories) {}
}
