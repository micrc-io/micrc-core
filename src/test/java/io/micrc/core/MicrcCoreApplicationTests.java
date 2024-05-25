package io.micrc.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrc.lib.JsonUtil;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootTest
class MicrcCoreApplicationTests {

    @Test
    void contextLoads() {
        assertTrue(true);
    }

    public static void main(String[] args) {

        // 实例：主题：事件：上下文：聚合：用例
        String s1 =
                "public:Windows:WindowSyncProgressSuccessEvent:orders:Orders:OrderSync\n" +
                        "public:Windows:WindowManualSyncEvent:stores:Windows:WindowSyncProgress\n" +
                        "public:Orders:OrderSyncFailureEvent:stores:Windows:WindowSyncTrack\n" +
                        "public:Orders:OrderSyncSuccessEvent:stores:Windows:WindowSyncTrack\n" +
                        "public:Windows:WindowLaunchRecoverEvent:stores:Windows:WindowLaunch\n" +
                        "public:Windows:WindowSyncBeginEvent:stores:Windows:WindowSyncProgress\n" +
                        "public:Windows:WindowConnectFailureEvent:stores:Windows:WindowConnectRetry\n" +
                        "public:Channel:ChannelOpenEvent:stores:Windows:WindowSyncRecover\n" +
                        "public:Channel:ChannelUnfreezeEvent:stores:Windows:WindowSyncRecover\n" +
                        "public:Channel:ChannelOpenEvent:stores:Windows:WindowConnectRecover\n" +
                        "public:Channel:ChannelUnfreezeEvent:stores:Windows:WindowConnectRecover\n" +
                        "public:Orders:OrderCreateFailureEvent:stores:Windows:WindowConnectComplete\n" +
                        "public:Orders:OrderCreateSuccessEvent:stores:Windows:WindowConnectComplete\n" +
                        "public:Windows:WindowConnectBeginEvent:orders:Orders:OrderRetrieval\n" +
                        "public:Windows:WindowInitLaunchSuccessEvent:stores:Windows:WindowConnectBegin\n" +
                        "public:Channel:ChannelUnfreezeEvent:stores:Windows:WindowLaunchRecover\n" +
                        "public:Windows:WindowLaunchRetryEvent:stores:Windows:WindowLaunch\n" +
                        "public:Windows:WindowInitEvent:stores:Windows:WindowLaunch\n" +
                        "public:Windows:WindowInitLaunchFailureEvent:stores:Windows:WindowLaunchRetry\n" +
                        "public:Channel:ChannelOpenEvent:stores:Windows:WindowLaunchRecover\n" +
                        "public:Channel:ChannelEnableEvent:stores:Windows:WindowInitLaunch\n" +
                        "public:Channel:ChannelMigrateEvent:stores:Store:StoreRecover\n" +
                        "public:Channel:ChannelMigrateEvent:stores:Store:StoreSurvival\n" +
                        "public:Store:StoreRegisterEvent:stores:Channel:ChannelEnable\n" +
                        "public:Channel:ChannelCreateEvent:stores:Store:StoreRegister\n" +
                        "public:BusinessAccount:BusinessAccountRecoverEvent:stores:Channel:ChannelUnfreeze\n" +
                        "public:BusinessAccount:BusinessAccountExceptionInvalidEvent:stores:Channel:ChannelFreeze\n" +
                        "public:BusinessAccount:BusinessAccountNormalInvalidEvent:stores:Channel:ChannelFreeze\n" +
                        "public:BusinessAccount:BusinessAccountCreateEvent:stores:Channel:ChannelCreate\n" +
                        "public:BusinessAccount:BusinessAccountNeedRenewalEvent:accounts:BusinessAccount:BusinessAccountAutoRenewal\n" +
                        "public:Orders:OrderPreprocessProductCompleteEvent:taxation:Invoice:InvoiceInit\n" +
                        "public:Invoice:InvoiceInitEvent:taxation:Invoice:InvoiceCheck\n" +
                        "public:Invoice:InvoiceCheckSuccessEvent:taxation:Invoice:InvoiceNeedCheck\n" +
                        "public:Invoice:InvoiceIssueEvent:taxation:Invoice:InvoiceGetContent\n" +
                        "public:Invoice:InvoiceGetContentEvent:taxation:Invoice:InvoiceSubmission\n" +
                        "public:Invoice:InvoiceSubmissionEvent:taxation:Invoice:InvoiceConfirmSubmissionResult\n" +
                        "public:Invoice:InvoiceConfirmSubmissionResultSuccessEvent:taxation:Invoice:InvoiceGeneratePrintText\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:taxation:Invoice:InvoiceNeedCheck\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:stores:Store:StoreInitTaxationPreference\n" +
                        "public:Invoice:InvoiceCreateEvent:taxation:Invoice:InvoiceIssue\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:stores:Store:StoreInitTaxationAccount\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:taxation:Consumers:ConsumersInit\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:taxation:StoreProductsTaxation:StoreProductsTaxationInit\n" +
                        "public:ShippingDocument:ShippingDocumentAutoShippingEvent:logistics:ShippingDocument:ShippingDocumentGetTrackingNumber\n" +
                        "public:ShippingDocument:ShippingDocumentGetShippingParameterEvent:logistics:ShippingDocument:ShippingDocumentAutoShipping\n" +
                        "public:Invoice:InvoiceCheckNeedEvent:taxation:Invoice:InvoiceCreate\n" +
                        "public:Invoice:InvoiceCheckNotNeedEvent:taxation:Invoice:InvoiceGeneratePrintText\n" +
                        "public:Invoice:InvoiceCheckSubmissionEvent:taxation:Invoice:InvoiceGeneratePrintText\n" +
                        "public:ShippingDocument:ShippingDocumentShippingEvent:logistics:LogisticsCarrier:LogisticsCarrierReport\n" +
                        "public:ShippingDocument:ShippingDocumentPlatformTemplateMissEvent:logistics:ShippingDocument:ShippingDocumentCreateDoc\n" +
                        "public:ShippingDocument:ShippingDocumentCreateDocSuccessEvent:logistics:ShippingDocument:ShippingDocumentCheckDoc\n" +
                        "public:ShippingDocument:ShippingDocumentCheckDocSuccessEvent:logistics:ShippingDocument:ShippingDocumentDownloadDoc\n" +
                        "public:Parcels:ParcelsDestroyEvent:logistics:ShippingDocument:ShippingDocumentDestroy\n" +
                        "public:ShippingDocument:ShippingDocumentCheckLogisticsPreferenceSuccessEvent:logistics:ShippingDocument:ShippingDocumentGeneratePrintText\n" +
                        "public:Windows:WindowInitLaunchSuccessEvent:stores:Windows:WindowInit\n" +
                        "public:Windows:WindowLaunchSuccessEvent:stores:Windows:WindowInit\n" +
                        "public:Windows:WindowConnectSuccessEvent:stores:Windows:WindowSyncBegin\n" +
                        "public:Windows:WindowLaunchFailureEvent:stores:Windows:WindowLaunchRetry\n" +
                        "public:Windows:WindowSyncFailureEvent:stores:Windows:WindowSyncProgress\n" +
                        "public:Windows:WindowSyncSuccessEvent:stores:Windows:WindowSyncBegin\n" +
                        "public:Windows:WindowLaunchSuccessEvent:stores:Windows:WindowConnectBegin\n" +
                        "public:Windows:WindowManualLaunchEvent:stores:Windows:WindowLaunch\n" +
                        "public:Windows:WindowConnectRecoverEvent:stores:Windows:WindowConnectBegin\n" +
                        "public:Windows:WindowConnectRetryEvent:stores:Windows:WindowConnectBegin\n" +
                        "public:Windows:WindowSyncRecoverEvent:stores:Windows:WindowSyncBegin\n" +
                        "public:Orders:OrderPreprocessorCreateEvent:orders:Orders:OrderGetOrderItems\n" +
                        "public:StoreProducts:ProductInitTerminateEvent:orders:Orders:OrderPreprocessProductComplete\n" +
                        "public:StoreProducts:ProductConfirmationCompletionEvent:orders:Orders:OrderPreprocessProductComplete\n" +
                        "public:StoreProducts:ProductMergeCompletionEvent:orders:Orders:OrderPreprocessProductComplete\n" +
                        "public:Orders:OrderPreprocessProductCompleteEvent:orders:Orders:OrderGetPacks\n" +
                        "public:Parcels:ParcelsInitTerminateEvent:orders:Orders:OrderPreprocessParcelComplete\n" +
                        "public:Parcels:ParcelsCreationCompletionEvent:orders:Orders:OrderPreprocessParcelComplete\n" +
                        "public:Parcels:ParcelsSyncCompletionEvent:orders:Orders:OrderPreprocessParcelComplete\n" +
                        "public:Orders:OrderSyncSuccessEvent:orders:Orders:OrderInitPreprocessor\n" +
                        "public:Orders:OrderCreateSuccessEvent:orders:Orders:OrderInitPreprocessor\n" +
                        "public:Channel:ChannelCreateEnableEvent:stores:Windows:WindowInitLaunch\n" +
                        "public:Orders:OrderGetOrderItemsSuccessEvent:production:StoreProducts:ProductsInit\n" +
                        "public:Orders:OrderSupplementSnEvent:production:StoreProducts:ProductsInit\n" +
                        "public:StoreProducts:ProductMergeCompletionEvent:production:StoreProducts:ProductsDestroy\n" +
                        "public:Orders:OrderGetPacksSuccessEvent:logistics:Parcels:ParcelsInit\n" +
                        "public:Parcels:ParcelsSyncCompletionEvent:logistics:Parcels:ParcelsDestroy\n" +
                        "public:Orders:OrderPreprocessPackageCompleteEvent:logistics:ShippingDocument:ShippingDocumentInit\n" +
                        "public:ShippingDocument:ShippingDocumentInitEvent:logistics:ShippingDocument:ShippingDocumentGetShippingParameter\n" +
                        "public:ShippingDocument:ShippingDocumentShippingEvent:logistics:ShippingDocument:ShippingDocumentGetTrackingNumber\n" +
                        "public:ShippingDocument:ShippingDocumentSupplementLogisticsPreferenceEvent:logistics:ShippingDocument:ShippingDocumentGeneratePrintText\n" +
                        "public:ShippingDocument:ShippingDocumentGetTrackingNumberEvent:logistics:ShippingDocument:ShippingDocumentGetTemplate\n" +
                        "public:PicketTicket:PicketTicketInvalidEvent:::\n" +
                        "public:::::\n" +
                        "public:PicketTicket:PicketTicketFlagParcelsEvent:::\n" +
                        "public:PicketTicket:PicketTicketInvalidEvent:inventory::PicketTicketPickingAdjustment\n" +
                        "public:ShippingDocument:ShippingDocumentGetTemplateSuccessEvent:logistics:ShippingDocument:ShippingDocumentCheckLogisticsPreference\n" +
                        "public:ShippingDocument:ShippingDocumentSupplementLogisticsPreferenceEvent:stores:Store:StoreInitLogisticsPreference\n" +
                        "public:Parcels:ParcelsFlagPicketTicketStateEvent:::\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:inventory::PicketTicketInvalid\n" +
                        "public:Parcels:ParcelsFlagStockOutStateEvent:inventory::PicketTicketStockOut\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:inventory::PicketTicketStockOut\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:inventory::PicketTicketPickingAdjustment\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:inventory::PicketTicketInvalidFinish\n" +
                        "public:TaxationAccount:TaxationAccountCheckRenewalEvent:accounts:TaxationAccount:TaxationAccountAutoRenewal\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:::PicketTicketMutationParcels\n" +
                        "public:PicketTicket:PicketTicketMutationParcelsEvent:logistics:Parcels:ParcelsRemovePicketTicketState\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:::\n" +
                        "public:PicketTicket:PicketTicketStockOutEvent:::\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:::\n" +
                        "public:Parcels:ParcelsFlagStockOutStateEvent:::\n" +
                        "public:PicketTicket:ParcelsRemovePicketTicketStateEvent:::\n" +
                        "public:Parcels:ParcelsFlagStockOutStateEvent:::\n" +
                        "public:Parcels:ParcelsFlagPicketTicketStateEvent:inventory::PicketTicketPreGeneratorAuthorize\n" +
                        "public:PicketTicket:PicketTicketFlagParcelsEvent:logistics:Parcels:ParcelsFlagPicketTicketState\n" +
                        "public:::::\n" +
                        "public:Parcels:ParcelsFlagStockOutStateEvent:inventory::PicketTicketStockOutMutationParcels\n" +
                        "public:Parcels:ParcelsFlagStockOutStateEvent:inventory::PicketTicketDamagedMutationParcels\n" +
                        "public:PicketTicket:PicketTicketStockOutMutationParcelsFinishEvent:logistics:Parcels:ParcelsFlagStockOutState\n" +
                        "public:PicketTicket:PicketTicketDamagedMutationParcelsFinishEvent:logistics:Parcels:ParcelsFlagStockOutState\n";

        String s2 =
                "public:Windows:WindowSyncProgressSuccessEvent:orders:Orders:OrderSync#UC-000185\n" +
                        "public:Windows:WindowManualSyncEvent:stores:Windows:WindowSyncProgress#UC-000203\n" +
                        "public:Orders:OrderSyncFailureEvent:stores:Windows:WindowSyncTrack#UC-000187\n" +
                        "public:Orders:OrderSyncSuccessEvent:stores:Windows:WindowSyncTrack#UC-000187\n" +
                        "public:Windows:WindowLaunchRecoverEvent:stores:Windows:WindowLaunch#UC-000182\n" +
                        "public:Windows:WindowSyncBeginEvent:stores:Windows:WindowSyncProgress#UC-000203\n" +
                        "public:Windows:WindowConnectFailureEvent:stores:Windows:WindowConnectRetry#UC-000201\n" +
                        "public:Channel:ChannelOpenEvent:stores:Windows:WindowSyncRecover#UC-000202\n" +
                        "public:Channel:ChannelUnfreezeEvent:stores:Windows:WindowSyncRecover#UC-000202\n" +
                        "public:Channel:ChannelOpenEvent:stores:Windows:WindowConnectRecover#UC-000200\n" +
                        "public:Channel:ChannelUnfreezeEvent:stores:Windows:WindowConnectRecover#UC-000200\n" +
                        "public:Orders:OrderCreateFailureEvent:stores:Windows:WindowConnectComplete#UC-000188\n" +
                        "public:Orders:OrderCreateSuccessEvent:stores:Windows:WindowConnectComplete#UC-000188\n" +
                        "public:Windows:WindowConnectBeginEvent:orders:Orders:OrderRetrieval#UC-000183\n" +
                        "public:Windows:WindowInitLaunchSuccessEvent:stores:Windows:WindowConnectBegin#UC-000186\n" +
                        "public:Channel:ChannelUnfreezeEvent:stores:Windows:WindowLaunchRecover#UC-000198\n" +
                        "public:Windows:WindowLaunchRetryEvent:stores:Windows:WindowLaunch#UC-000182\n" +
                        "public:Windows:WindowInitEvent:stores:Windows:WindowLaunch#UC-000182\n" +
                        "public:Windows:WindowInitLaunchFailureEvent:stores:Windows:WindowLaunchRetry#UC-000199\n" +
                        "public:Channel:ChannelOpenEvent:stores:Windows:WindowLaunchRecover#UC-000198\n" +
                        "public:Channel:ChannelEnableEvent:stores:Windows:WindowInitLaunch#UC-000180\n" +
                        "public:Channel:ChannelMigrateEvent:stores:Store:StoreRecover#UC-000171\n" +
                        "public:Channel:ChannelMigrateEvent:stores:Store:StoreSurvival#UC-000169\n" +
                        "public:Store:StoreRegisterEvent:stores:Channel:ChannelEnable#UC-000168\n" +
                        "public:Channel:ChannelCreateEvent:stores:Store:StoreRegister#UC-000167\n" +
                        "public:BusinessAccount:BusinessAccountRecoverEvent:stores:Channel:ChannelUnfreeze#UC-000178\n" +
                        "public:BusinessAccount:BusinessAccountExceptionInvalidEvent:stores:Channel:ChannelFreeze#UC-000172\n" +
                        "public:BusinessAccount:BusinessAccountNormalInvalidEvent:stores:Channel:ChannelFreeze#UC-000172\n" +
                        "public:BusinessAccount:BusinessAccountCreateEvent:stores:Channel:ChannelCreate#UC-000166\n" +
                        "public:BusinessAccount:BusinessAccountNeedRenewalEvent:accounts:BusinessAccount:BusinessAccountAutoRenewal#UC-000161\n" +
                        "public:Orders:OrderPreprocessProductCompleteEvent:taxation:Invoice:InvoiceInit#UC-000052\n" +
                        "public:Invoice:InvoiceInitEvent:taxation:Invoice:InvoiceCheck#UC-000239\n" +
                        "public:Invoice:InvoiceCheckSuccessEvent:taxation:Invoice:InvoiceNeedCheck#UC-000261\n" +
                        "public:Invoice:InvoiceIssueEvent:taxation:Invoice:InvoiceGetContent#UC-000242\n" +
                        "public:Invoice:InvoiceGetContentEvent:taxation:Invoice:InvoiceSubmission#UC-000243\n" +
                        "public:Invoice:InvoiceSubmissionEvent:taxation:Invoice:InvoiceConfirmSubmissionResult#UC-000244\n" +
                        "public:Invoice:InvoiceConfirmSubmissionResultSuccessEvent:taxation:Invoice:InvoiceGeneratePrintText#UC-000245\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:taxation:Invoice:InvoiceNeedCheck#UC-000261\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:stores:Store:StoreInitTaxationPreference#UC-000026\n" +
                        "public:Invoice:InvoiceCreateEvent:taxation:Invoice:InvoiceIssue#UC-000241\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:stores:Store:StoreInitTaxationAccount#UC-000029\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:taxation:Consumers:ConsumersInit#UC-000049\n" +
                        "public:Invoice:InvoiceSupplementTaxationEvent:taxation:StoreProductsTaxation:StoreProductsTaxationInit#UC-000086\n" +
                        "public:ShippingDocument:ShippingDocumentAutoShippingEvent:logistics:ShippingDocument:ShippingDocumentGetTrackingNumber#UC-000021\n" +
                        "public:ShippingDocument:ShippingDocumentGetShippingParameterEvent:logistics:ShippingDocument:ShippingDocumentAutoShipping#UC-000253\n" +
                        "public:Invoice:InvoiceCheckNeedEvent:taxation:Invoice:InvoiceCreate#UC-000309\n" +
                        "public:Invoice:InvoiceCheckNotNeedEvent:taxation:Invoice:InvoiceGeneratePrintText#UC-000245\n" +
                        "public:Invoice:InvoiceCheckSubmissionEvent:taxation:Invoice:InvoiceGeneratePrintText#UC-000245\n" +
                        "public:ShippingDocument:ShippingDocumentShippingEvent:logistics:LogisticsCarrier:LogisticsCarrierReport#UC-000262\n" +
                        "public:ShippingDocument:ShippingDocumentPlatformTemplateMissEvent:logistics:ShippingDocument:ShippingDocumentCreateDoc#UC-000258\n" +
                        "public:ShippingDocument:ShippingDocumentCreateDocSuccessEvent:logistics:ShippingDocument:ShippingDocumentCheckDoc#UC-000259\n" +
                        "public:ShippingDocument:ShippingDocumentCheckDocSuccessEvent:logistics:ShippingDocument:ShippingDocumentDownloadDoc#UC-000260\n" +
                        "public:Parcels:ParcelsDestroyEvent:logistics:ShippingDocument:ShippingDocumentDestroy#UC-000266\n" +
                        "public:ShippingDocument:ShippingDocumentCheckLogisticsPreferenceSuccessEvent:logistics:ShippingDocument:ShippingDocumentGeneratePrintText#UC-000024\n" +
                        "public:Windows:WindowInitLaunchSuccessEvent:stores:Windows:WindowInit#UC-000181\n" +
                        "public:Windows:WindowLaunchSuccessEvent:stores:Windows:WindowInit#UC-000181\n" +
                        "public:Windows:WindowConnectSuccessEvent:stores:Windows:WindowSyncBegin#UC-000184\n" +
                        "public:Windows:WindowLaunchFailureEvent:stores:Windows:WindowLaunchRetry#UC-000199\n" +
                        "public:Windows:WindowSyncFailureEvent:stores:Windows:WindowSyncProgress#UC-000203\n" +
                        "public:Windows:WindowSyncSuccessEvent:stores:Windows:WindowSyncBegin#UC-000184\n" +
                        "public:Windows:WindowLaunchSuccessEvent:stores:Windows:WindowConnectBegin#UC-000186\n" +
                        "public:Windows:WindowManualLaunchEvent:stores:Windows:WindowLaunch#UC-000182\n" +
                        "public:Windows:WindowConnectRecoverEvent:stores:Windows:WindowConnectBegin#UC-000186\n" +
                        "public:Windows:WindowConnectRetryEvent:stores:Windows:WindowConnectBegin#UC-000186\n" +
                        "public:Windows:WindowSyncRecoverEvent:stores:Windows:WindowSyncBegin#UC-000184\n" +
                        "public:Orders:OrderPreprocessorCreateEvent:orders:Orders:OrderGetOrderItems#UC-000227\n" +
                        "public:StoreProducts:ProductInitTerminateEvent:orders:Orders:OrderPreprocessProductComplete#UC-000215\n" +
                        "public:StoreProducts:ProductConfirmationCompletionEvent:orders:Orders:OrderPreprocessProductComplete#UC-000215\n" +
                        "public:StoreProducts:ProductMergeCompletionEvent:orders:Orders:OrderPreprocessProductComplete#UC-000215\n" +
                        "public:Orders:OrderPreprocessProductCompleteEvent:orders:Orders:OrderGetPacks#UC-000214\n" +
                        "public:Parcels:ParcelsInitTerminateEvent:orders:Orders:OrderPreprocessParcelComplete#UC-000213\n" +
                        "public:Parcels:ParcelsCreationCompletionEvent:orders:Orders:OrderPreprocessParcelComplete#UC-000213\n" +
                        "public:Parcels:ParcelsSyncCompletionEvent:orders:Orders:OrderPreprocessParcelComplete#UC-000213\n" +
                        "public:Orders:OrderSyncSuccessEvent:orders:Orders:OrderInitPreprocessor#UC-000226\n" +
                        "public:Orders:OrderCreateSuccessEvent:orders:Orders:OrderInitPreprocessor#UC-000226\n" +
                        "public:Channel:ChannelCreateEnableEvent:stores:Windows:WindowInitLaunch#UC-000180\n" +
                        "public:Orders:OrderGetOrderItemsSuccessEvent:production:StoreProducts:ProductsInit#UC-000228\n" +
                        "public:Orders:OrderSupplementSnEvent:production:StoreProducts:ProductsInit#UC-000228\n" +
                        "public:StoreProducts:ProductMergeCompletionEvent:production:StoreProducts:ProductsDestroy#UC-000230\n" +
                        "public:Orders:OrderGetPacksSuccessEvent:logistics:Parcels:ParcelsInit#UC-000225\n" +
                        "public:Parcels:ParcelsSyncCompletionEvent:logistics:Parcels:ParcelsDestroy#UC-000232\n" +
                        "public:Orders:OrderPreprocessPackageCompleteEvent:logistics:ShippingDocument:ShippingDocumentInit#UC-000025\n" +
                        "public:ShippingDocument:ShippingDocumentInitEvent:logistics:ShippingDocument:ShippingDocumentGetShippingParameter#UC-000022\n" +
                        "public:ShippingDocument:ShippingDocumentShippingEvent:logistics:ShippingDocument:ShippingDocumentGetTrackingNumber#UC-000021\n" +
                        "public:ShippingDocument:ShippingDocumentSupplementLogisticsPreferenceEvent:logistics:ShippingDocument:ShippingDocumentGeneratePrintText#UC-000024\n" +
                        "public:ShippingDocument:ShippingDocumentGetTrackingNumberEvent:logistics:ShippingDocument:ShippingDocumentGetTemplate#UC-000256\n" +
                        "public:Parcels:ParcelsCancleMarkPicketTicket:inventory:PicketTicket:PicketTicketLackMutationComplete#UC-000316\n" +
                        "public:Parcels:ParcelsCancleMarkPicketTicket:inventory:PicketTicket:PicketTicketDamageMutationComplate#UC-000318\n" +
                        "public:Parcels:ParcelsCancleMarkPicketTicket:inventory:PicketTicket:PicketTicketInvalidComplete#UC-000319\n" +
                        "public:Parcels:ParcelsCancleMarkPicketTicket:inventory:PicketTicket:PicketTicketMutationParcelsComplete#UC-000314\n" +
                        "public:ShippingDocument:ShippingDocumentGetTemplateSuccessEvent:logistics:ShippingDocument:ShippingDocumentCheckLogisticsPreference#UC-000287\n" +
                        "public:ShippingDocument:ShippingDocumentSupplementLogisticsPreferenceEvent:stores:Store:StoreInitLogisticsPreference#UC-000013\n" +
                        "public:PicketTicket:PicketTicketMutationParcelsEvent:logistics:Parcels:ParcelsCancleMarkPicketTicket#UC-000291\n" +
                        "public:PicketTicket:PicketTicketLackMutationEvent:logistics:Parcels:ParcelsCancleMarkPicketTicket#UC-000291\n" +
                        "public:PicketTicket:PicketTicketDamageMutationEvent:logistics:Parcels:ParcelsCancleMarkPicketTicket#UC-000291\n" +
                        "public:PicketTicket:PicketTicketInvalidEvent:logistics:Parcels:ParcelsCancleMarkPicketTicket#UC-000291\n" +
                        "public:TaxationAccount:TaxationAccountCheckRenewalEvent:accounts:TaxationAccount:TaxationAccountAutoRenewal#UC-000304\n" +
                        "public:Parcels:ParcelsMarkPicketTicket:inventory:PicketTicket:PicketTicketAuthorizeComplete#UC-000338\n" +
                        "public:PicketTicket:PicketTicketAuthorizeEvent:logistics:Parcels:ParcelsMarkPicketTicket#UC-000290\n" +
                        "public:::::#\n";

//        // 原组
//        HashMap<String, List<String>> groups1 = group(s1);
//        ArrayList<Item> objects1 = transformAggrList(groups1);

        // 新组
        HashMap<String, List<String>> groups2 = group(s2);
        ArrayList<Item> objects2 = transformAggrList(groups2);

//        // 检查
//        checkChange(objects1, objects2);

        // 结构
        Map<String, Map<String, String>> collect = objects2.stream().collect(Collectors.groupingBy(Item::getContext, Collectors.groupingBy(Item::getLogic,
                Collectors.mapping(obj -> obj.getEvent() + "/" + obj.getGroup() + "/" + obj.getTopic(),
                        // | 前面需要拼接发送事件
                        Collectors.joining(",", "|", "")
                ))));

        // 拼接发送事件
        System.out.println("{上下文:{用例:发送事件/发送主题|监听事件/监听组/监听主题}}: " + JsonUtil.writeValueAsString(collect));
    }

    private static void checkChange(ArrayList<Item> objects1, ArrayList<Item> objects2) {
        for (int i = 0; i < objects1.size(); i++) {
            Item o = objects1.get(i);
            Item n = null;
            for (int j = 0; j < objects2.size(); j++) {
                Item t = objects2.get(j);
                if (t.getLogic().equals(o.getLogic()) && t.getEvent().equals(o.getEvent())) {
                    n = t;
                    break;
                }
            }
            if (n == null || !o.getGroup().equals(n.getGroup())) {
                System.out.println("消费组改变: " + o.getEvent() + o.getLogic() + ": " + o.getGroup() + " -> " + (n == null ? null : n.getGroup()));
            }
        }
    }

    @NotNull
    private static ArrayList<Item> transformAggrList(HashMap<String, List<String>> groups1) {
        ArrayList<Item> objects1 = new ArrayList<>();
        groups1.forEach((g, ms) -> {
            String[] split = g.split("_");
            ms.forEach(e -> {
                String[] split1 = e.split("_");
                String[] logicArr = split1[2].split("#");
                Item aggregation = Item.builder()
                        .context(split[0]).aggregation(split[1]).group(g)
                        .topic(split1[0]).event(split1[1]).logic(logicArr[1] + ":" + logicArr[0])
                        .build();
                objects1.add(aggregation);
            });
        });
        return objects1;
    }

    @Data
    @Builder
    public static class Item {
        private String context;
        private String aggregation;
        private String group;
        private String topic;
        private String event;
        private String logic;
    }

    private static HashMap<String, List<String>> group(String a) {
        HashMap<String, HashMap<String, Object>> aggregationMap = new HashMap<>();
        ArrayList<String[]> list = Arrays.stream(a.split("\n"))
                .map(e -> (e + ":x").split(":", -1))
                .filter(e1 -> Arrays.stream(e1).noneMatch(String::isEmpty))
                .collect(Collectors.toCollection(ArrayList::new));
        int i = 0;
        HashMap<String, List<String>> aggregationGroups = new HashMap<>();
        while (!list.isEmpty()) {
            for (String[] event : list) {
                String groupId = event[3] + "_" + event[4] + "_" + event[0] + "_" + i;
                event[6] = "";
                ArrayList<String> mappingList;
                String mapping = event[1] + "_" + event[2] + "_" + event[5];
                if (aggregationGroups.containsKey(groupId)) {
                    mappingList = (ArrayList<String>) aggregationGroups.get(groupId);
                    if (mappingList.stream().anyMatch(m -> event[2].equals(m.split("_")[1]))) {
                        event[6] = "next";
                    } else {
                        mappingList.add(mapping);
                    }
                } else {
                    mappingList = new ArrayList<>();
                    mappingList.add(mapping);
                    aggregationGroups.put(groupId, mappingList);
                }
            }
            i++;
            list.removeIf(e -> !"next".equals(e[6]));
        }
        System.out.println("{消费组:[主题_事件_用例]}: " + JsonUtil.writeValueAsString(aggregationGroups));
        return aggregationGroups;
    }
}
