/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.lib.kafka;

import com.streamsets.pipeline.api.ChooserMode;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.Label;
import com.streamsets.pipeline.api.RawSource;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.ValueChooser;
import com.streamsets.pipeline.lib.json.StreamingJsonParser;

@GenerateResourceBundle
@StageDef(
    version = "0.0.1",
    label = "Kafka Consumer",
    description = "Reads messages from Kafka brokers. Message data can be: LOG, CSV, TSV, XML or JSON",
    icon = "kafka.png"
)
@RawSource(rawSourcePreviewer = KafkaRawSourcePreviewer.class, mimeType = "application/json")
@ConfigGroups(value = HighLevelKafkaSource.Groups.class)
public class HighLevelKafkaSource extends HighLevelAbstractKafkaSource {

  public enum Groups implements Label {
    JSON("JSON Data"),
    CSV("CSV Data")

    ;

    private final String label;

    private Groups(String label) {
      this.label = label;
    }

    public String getLabel() {
      return this.label;
    }
  }

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "MULTIPLE_OBJECTS",
      label = "JSON Content",
      description = "Indicates if the JSON files have a single JSON array object or multiple JSON objects",
      displayPosition = 100,
      group = "JSON",
      dependsOn = "consumerPayloadType",
      triggeredByValue = "JSON"
  )
  @ValueChooser(type = ChooserMode.PROVIDED, chooserValues = JsonEventModeChooserValues.class)
  public StreamingJsonParser.Mode jsonContent;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.INTEGER,
      defaultValue = "4096",
      label = "Maximum JSON Object Length",
      description = "The maximum length for a JSON Object being converted to a record, if greater the full JSON " +
                    "object is discarded and processing continues with the next JSON object",
      displayPosition = 110,
      group = "JSON",
      dependsOn = "consumerPayloadType",
      triggeredByValue = "JSON"
  )
  public int maxJsonObjectLen;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "CSV",
      label = "CSV Format",
      description = "The specific CSV format of the files",
      displayPosition = 200,
      group = "CSV",
      dependsOn = "consumerPayloadType",
      triggeredByValue = "CSV"
  )
  @ValueChooser(type = ChooserMode.PROVIDED, chooserValues = CvsFileModeChooserValues.class)
  public CsvFileMode csvFileFormat;

  private FieldCreator fieldCreator;

  @Override
  public void init() throws StageException {
    super.init();
    switch ((consumerPayloadType)) {
      case JSON:
        fieldCreator = new JsonFieldCreator(jsonContent, maxJsonObjectLen);
        break;
      case LOG:
        fieldCreator = new LogFieldCreator();
        break;
      case CSV:
        fieldCreator = new CsvFieldCreator(csvFileFormat);
        break;
      case XML:
        fieldCreator = new XmlFieldCreator();
        break;
      default :
    }
  }

  @Override
  protected Field createField(byte[] bytes) throws StageException {
    return fieldCreator.createField(bytes);
  }
}
