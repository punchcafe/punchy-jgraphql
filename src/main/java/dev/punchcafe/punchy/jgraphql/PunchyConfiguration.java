package dev.punchcafe.punchy.jgraphql;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties("graphql.punchy")
public class PunchyConfiguration {
    List<String> schemaFiles;
}
