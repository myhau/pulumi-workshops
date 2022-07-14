package myproject;

import com.pulumi.Pulumi;
import com.pulumi.asset.StringAsset;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.azurenative.resources.ResourcesFunctions;
import com.pulumi.azurenative.resources.inputs.GetResourceGroupArgs;
import com.pulumi.azurenative.storage.*;
import com.pulumi.azurenative.storage.enums.AccessTier;
import com.pulumi.azurenative.storage.enums.Kind;
import com.pulumi.azurenative.storage.enums.SkuName;
import com.pulumi.azurenative.storage.inputs.ListStorageAccountKeysArgs;
import com.pulumi.azurenative.storage.inputs.SkuArgs;
import com.pulumi.core.Output;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // PLAN
            // storage account
            // blob container
            // create an example binary file (blob)

            var environment = ctx.config().require("environment");

            var resourceGroup = new ResourceGroup("uploader-app",
                    ResourceGroupArgs.builder()
                            // NOTE: uncomment if you want to control the name of your resource group (and not use the auto-naming)
                            // .resourceGroupName("uploader-app-" + environment)
                            .build()
            );

            var storageAccount = new StorageAccount("uploaderstorage",
                    StorageAccountArgs.builder()
                            // NOTE: uncomment if you want to control the name of your resource group (and not use the auto-naming)
                            // .accountName("uploaderstorage" + environment)
                            .kind(Kind.BlobStorage)
                            .accessTier(AccessTier.Hot)
                            .sku(
                                    SkuArgs.builder()
                                            .name(SkuName.Standard_LRS)
                                            .build()
                            )
                            .resourceGroupName(resourceGroup.name())
                            .build()
            );

            var blobContainer = new BlobContainer("uploader-blobs",
                    BlobContainerArgs.builder()
                            .resourceGroupName(resourceGroup.name())
                            .accountName(storageAccount.name())
                            .build()
            );

            var source = new StringAsset("Hello world!");

            var exampleBlob = new Blob("uploader-example-blob",
                    BlobArgs.builder()
                            .resourceGroupName(resourceGroup.name())
                            .accountName(storageAccount.name())
                            .containerName(blobContainer.name())
                            .source(source)
                            .build()
            );

            var storageAccountKeysResult = mapOutputs(resourceGroup.name(), storageAccount.name(),
                    (group, account) -> StorageFunctions.listStorageAccountKeys(
                            ListStorageAccountKeysArgs.builder()
                                    .resourceGroupName(group)
                                    .accountName(account)
                                    .build()
                    )
            );

            /* NOTE: uncomment if you want to reference some existing resource group
            var existingResourceGroupId = Output
                    .of(
                            ResourcesFunctions.getResourceGroup(
                                    GetResourceGroupArgs.builder()
                                            .resourceGroupName("YOUR-RESOURCE-GROUP-NAME")
                                            .build()
                            )
                    )
                    .applyValue(group -> group.id());
            ctx.export("existingResourceGroupId", existingResourceGroupId);
            */

            var storageAccountSecretKey = storageAccountKeysResult.applyValue(list -> list.keys().get(0).value()).asSecret();

            ctx.export("accountName", storageAccount.name());
            ctx.export("resourceGroupName", resourceGroup.name());
            ctx.export("blobContainerName", blobContainer.name());
            ctx.export("blobName", exampleBlob.name());
            ctx.export("storageAccountSecretKey", storageAccountSecretKey);
        });
    }

    private static <A, B, T> Output<T> mapOutputs(Output<A> first, Output<B> second, BiFunction<A, B, CompletableFuture<T>> mapper) {
        return Output.tuple(first, second).apply(tuple ->
                Output.of(
                        mapper.apply(tuple.t1, tuple.t2)
                )
        );
    }
}
