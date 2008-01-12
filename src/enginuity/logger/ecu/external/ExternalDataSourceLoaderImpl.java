package enginuity.logger.ecu.external;

import enginuity.logger.ecu.definition.plugin.PluginFilenameFilter;
import enginuity.logger.ecu.exception.ConfigurationException;
import static enginuity.util.ParamChecker.isNullOrEmpty;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ExternalDataSourceLoaderImpl implements ExternalDataSourceLoader {
    private static final Logger LOGGER = Logger.getLogger(ExternalDataSourceLoaderImpl.class);
    private List<ExternalDataSource> externalDataSources = new ArrayList<ExternalDataSource>();

    public void loadExternalDataSources() {
        try {
            File pluginsDir = new File("./plugins");
            if (pluginsDir.exists() && pluginsDir.isDirectory()) {
                externalDataSources = new ArrayList<ExternalDataSource>();
                File[] pluginPropertyFiles = pluginsDir.listFiles(new PluginFilenameFilter());
                for (File pluginPropertyFile : pluginPropertyFiles) {
                    Properties pluginProps = new Properties();
                    FileInputStream inputStream = new FileInputStream(pluginPropertyFile);
                    try {
                        pluginProps.load(inputStream);
                        String datasourceClassName = pluginProps.getProperty("datasource.class");
                        if (!isNullOrEmpty(datasourceClassName)) {
                            try {
                                Class<?> dataSourceClass = getClass().getClassLoader().loadClass(datasourceClassName);
                                if (dataSourceClass != null && ExternalDataSource.class.isAssignableFrom(dataSourceClass)) {
                                    ExternalDataSource dataSource = (ExternalDataSource) dataSourceClass.newInstance();
                                    ExternalDataSource managedDataSource = new GenericDataSourceManager(dataSource);
                                    externalDataSources.add(managedDataSource);
                                    LOGGER.info("Plugin loaded: " + dataSource.getName() + " v" + dataSource.getVersion());
                                }
                            } catch (Throwable t) {
                                LOGGER.error("Error loading external datasource: " + datasourceClassName + ", specified in: "
                                        + pluginPropertyFile.getAbsolutePath(), t);
                            }
                        }
                    } finally {
                        inputStream.close();
                    }
                }
            }
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }

    public List<ExternalDataSource> getExternalDataSources() {
        return externalDataSources;
    }
}
