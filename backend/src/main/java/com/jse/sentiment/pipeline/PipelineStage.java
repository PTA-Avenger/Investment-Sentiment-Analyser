package com.jse.sentiment.pipeline;

public interface PipelineStage<I, O> {
    O process(I input) throws Exception;
}
