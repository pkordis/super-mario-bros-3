package house.x1337.app.smb3.model;

import java.util.concurrent.CompletableFuture;

public record Pending<I, O>(
    I completable,
    CompletableFuture<O> completion
) {}
