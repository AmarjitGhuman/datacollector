/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.hdfs;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.streamsets.pipeline.api.Batch;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.hdfs.writer.RecordWriter;

import javax.servlet.jsp.el.ELException;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

@GenerateResourceBundle
@StageDef(
    version = "1.0.0",
    label = "Hadoop FS",
    description = "Writes records to files in a Hadoop FS",
    icon = "hdfs.svg"
)
public class HdfsTarget extends BaseHdfsTarget {
    private Counter toHdfsRecordsCounter;
    private Meter toHdfsRecordsMeter;
    private Counter lateRecordsCounter;
    private Meter lateRecordsMeter;

    @Override
    protected void init() throws StageException {
        super.init();
        toHdfsRecordsCounter = getContext().createCounter("toHdfsRecords");
        toHdfsRecordsMeter = getContext().createMeter("toHdfsRecords");
        lateRecordsCounter = getContext().createCounter("lateRecords");
        lateRecordsMeter = getContext().createMeter("lateRecords");
    }

    @Override
    public void processBatch(Batch batch) throws StageException {
        try {
            getCurrentWriters().purge();
            if (getLateWriters() != null) {
                getLateWriters().purge();
            }
            Iterator<Record> it = batch.getRecords();
            while (it.hasNext()) {
                Record record = it.next();
                Date recordTime = getRecordTime(record);
                RecordWriter writer = getCurrentWriters().get(getBatchTime(), recordTime, record);
                if (writer != null) {
                    toHdfsRecordsCounter.inc();
                    toHdfsRecordsMeter.mark();
                    writer.write(record);
                    getCurrentWriters().release(writer);
                } else {
                    lateRecordsCounter.inc();
                    lateRecordsMeter.mark();
                    switch (lateRecordsAction) {
                        case DISCARD:
                            break;
                        case SEND_TO_ERROR:
                            getContext().toError(record, HdfsLibError.HDFS_0015, record.getHeader().getSourceId());
                            break;
                        case SEND_TO_LATE_RECORDS_FILE:
                            RecordWriter lateWriter = getLateWriters().get(getBatchTime(), getBatchTime(), record);
                            lateWriter.write(record);
                            getLateWriters().release(lateWriter);
                            break;
                    }
                }
            }
        } catch (IOException|ELException ex) {
            throw new StageException(HdfsLibError.HDFS_0016, ex.getMessage(), ex);
        }
    }
}
