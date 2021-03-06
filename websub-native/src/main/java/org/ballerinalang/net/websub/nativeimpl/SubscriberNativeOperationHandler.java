/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.websub.nativeimpl;

import org.ballerinalang.jvm.api.BStringUtils;
import org.ballerinalang.jvm.api.BValueCreator;
import org.ballerinalang.jvm.api.values.BMap;
import org.ballerinalang.jvm.api.values.BObject;
import org.ballerinalang.jvm.api.values.BString;
import org.ballerinalang.jvm.scheduling.Scheduler;
import org.ballerinalang.jvm.types.BArrayType;
import org.ballerinalang.jvm.types.BMapType;
import org.ballerinalang.jvm.types.BRecordType;
import org.ballerinalang.jvm.types.BTypes;
import org.ballerinalang.jvm.util.exceptions.BallerinaConnectorException;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.TypedescValue;
import org.ballerinalang.net.http.HttpConnectorPortBindingListener;
import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.net.http.HttpService;
import org.ballerinalang.net.http.websocket.server.WebSocketServicesRegistry;
import org.ballerinalang.net.websub.BallerinaWebSubConnectorListener;
import org.ballerinalang.net.websub.WebSubHttpService;
import org.ballerinalang.net.websub.WebSubServicesRegistry;
import org.ballerinalang.net.websub.WebSubSubscriberConstants;
import org.ballerinalang.net.websub.WebSubUtils;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;

import java.util.HashMap;
import java.util.Optional;

import static org.ballerinalang.net.http.HttpConstants.DEFAULT_HOST;
import static org.ballerinalang.net.http.HttpConstants.HTTP_DEFAULT_HOST;
import static org.ballerinalang.net.http.HttpConstants.HTTP_SERVER_CONNECTOR;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_NAME_WEBSUB_SUBSCRIBER_SERVICE_CONFIG;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_CALLBACK;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_EXPECT_INTENT_VERIFICATION;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_LEASE_SECONDS;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_SECRET;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_SUBSCRIBE_ON_STARTUP;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_SUBSCRIPTION_HUB_CLIENT_CONFIG;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_SUBSCRIPTION_PUBLISHER_CLIENT_CONFIG;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ANN_WEBSUB_ATTR_TARGET;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ENDPOINT_CONFIG_HOST;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ENDPOINT_CONFIG_PORT;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.ENDPOINT_CONFIG_SECURE_SOCKET_CONFIG;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.EXTENSION_CONFIG_HEADER_AND_PAYLOAD_KEY_RESOURCE_MAP;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.EXTENSION_CONFIG_HEADER_RESOURCE_MAP;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.EXTENSION_CONFIG_PAYLOAD_KEY_RESOURCE_MAP;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.EXTENSION_CONFIG_TOPIC_HEADER;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.EXTENSION_CONFIG_TOPIC_IDENTIFIER;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.LISTENER_SERVICE_ENDPOINT;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.LISTENER_SERVICE_ENDPOINT_CONFIG;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.SERVICE_CONFIG_EXTENSION_CONFIG;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.SERVICE_ENDPOINT_CONFIG_NAME;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.TOPIC_ID_HEADER;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.TOPIC_ID_HEADER_AND_PAYLOAD;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.TOPIC_ID_PAYLOAD_KEY;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.WEBSUB_HTTP_ENDPOINT;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.WEBSUB_PACKAGE_FULL_QUALIFIED_NAME;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.WEBSUB_SERVICE_NAME;
import static org.ballerinalang.net.websub.WebSubSubscriberConstants.WEBSUB_SERVICE_REGISTRY;

/**
 * This class contains interop external functions related to WebSub Subscriber listener.
 *
 * @since 1.1.0
 */
public class SubscriberNativeOperationHandler {

    private static final BArrayType mapArrayType = new BArrayType(new BMapType(BTypes.typeAny));

    /**
     * Initialize the WebSub subscriber endpoint.
     *
     * @param subscriberServiceListener the subscriber listener
     */
    @SuppressWarnings("unchecked")
    public static void initWebSubSubscriberServiceEndpoint(BObject subscriberServiceListener) {

        BObject serviceEndpoint = (BObject) subscriberServiceListener.get(
                BStringUtils.fromString(WEBSUB_HTTP_ENDPOINT));
        BMap<BString, Object> config = (BMap<BString, Object>) subscriberServiceListener.get(
                BStringUtils.fromString(LISTENER_SERVICE_ENDPOINT_CONFIG));
        WebSubServicesRegistry webSubServicesRegistry;

        if (config == null || config.get(BStringUtils.fromString(SERVICE_CONFIG_EXTENSION_CONFIG)) == null) {
            webSubServicesRegistry = new WebSubServicesRegistry(new WebSocketServicesRegistry());
        } else {
            BMap<BString, Object> extensionConfig = (BMap<BString, Object>) config.get(
                    BStringUtils.fromString(SERVICE_CONFIG_EXTENSION_CONFIG));
            String topicIdentifier = extensionConfig.getStringValue(
                    BStringUtils.fromString(EXTENSION_CONFIG_TOPIC_IDENTIFIER)).getValue();
            String topicHeader = null;
            BMap<BString, Object> headerResourceMap = null;
            BMap<BString, BMap<BString, Object>> payloadKeyResourceMap = null;
            BMap<BString, BMap<BString, BMap<BString, Object>>> headerAndPayloadKeyResourceMap = null;

            if (TOPIC_ID_HEADER.equals(topicIdentifier) || TOPIC_ID_HEADER_AND_PAYLOAD.equals(topicIdentifier)) {
                topicHeader = extensionConfig.getStringValue(
                        BStringUtils.fromString(EXTENSION_CONFIG_TOPIC_HEADER)).getValue();
                if (topicHeader == null) {
                    throw new BallerinaConnectorException("Topic Header not specified to dispatch by "
                                                                  + topicIdentifier);
                }
            }

            if (TOPIC_ID_HEADER.equals(topicIdentifier)) {
                headerResourceMap = (BMap<BString, Object>) extensionConfig.get(
                        EXTENSION_CONFIG_HEADER_RESOURCE_MAP);
                if (headerResourceMap == null) {
                    throw new BallerinaConnectorException("Resource map not specified to dispatch by header");
                }
            } else if (TOPIC_ID_HEADER_AND_PAYLOAD.equals(topicIdentifier)) {
                headerAndPayloadKeyResourceMap = (BMap<BString, BMap<BString, BMap<BString, Object>>>)
                        extensionConfig.get(EXTENSION_CONFIG_HEADER_AND_PAYLOAD_KEY_RESOURCE_MAP);
                if (headerAndPayloadKeyResourceMap == null) {
                    throw new BallerinaConnectorException("Resource map not specified to dispatch by header and "
                                                                  + "payload");
                }
                headerResourceMap = (BMap<BString, Object>) extensionConfig.get(
                        EXTENSION_CONFIG_HEADER_RESOURCE_MAP);
                payloadKeyResourceMap = (BMap<BString, BMap<BString, Object>>) extensionConfig.get(
                        EXTENSION_CONFIG_PAYLOAD_KEY_RESOURCE_MAP);
            } else {
                payloadKeyResourceMap = (BMap<BString, BMap<BString, Object>>) extensionConfig.get(
                        EXTENSION_CONFIG_PAYLOAD_KEY_RESOURCE_MAP);
                if (payloadKeyResourceMap == null) {
                    throw new BallerinaConnectorException("Resource map not specified to dispatch by payload");
                }
            }
            HashMap<String, BRecordType> resourceDetails = buildResourceDetailsMap(topicIdentifier, headerResourceMap,
                                                                                   payloadKeyResourceMap,
                                                                                   headerAndPayloadKeyResourceMap);
            webSubServicesRegistry = new WebSubServicesRegistry(new WebSocketServicesRegistry(), topicIdentifier,
                                                                topicHeader, headerResourceMap, payloadKeyResourceMap,
                                                                headerAndPayloadKeyResourceMap, resourceDetails);
        }

        serviceEndpoint.addNativeData(WEBSUB_SERVICE_REGISTRY, webSubServicesRegistry);
    }

    private static HashMap<String, BRecordType> buildResourceDetailsMap(String topicIdentifier,
                                                                        BMap<BString, Object> headerResourceMap,
                                                                        BMap<BString, BMap<BString, Object>>
                                                                                payloadKeyResourceMap,
                                                                        BMap<BString, BMap<BString,
                                                                                BMap<BString, Object>>>
                                                                                headerAndPayloadKeyResourceMap) {
        // Map with resource details where the key is the resource name and the value is the param
        HashMap<String, BRecordType> resourceDetails = new HashMap<>();
        if (topicIdentifier != null) {
            switch (topicIdentifier) {
                case TOPIC_ID_HEADER:
                    populateResourceDetailsByHeader(headerResourceMap, resourceDetails);
                    break;
                case TOPIC_ID_PAYLOAD_KEY:
                    populateResourceDetailsByPayload(payloadKeyResourceMap, resourceDetails);
                    break;
                default:
                    populateResourceDetailsByHeaderAndPayload(headerAndPayloadKeyResourceMap, resourceDetails);
                    if (headerResourceMap != null) {
                        populateResourceDetailsByHeader(headerResourceMap, resourceDetails);
                    }
                    if (payloadKeyResourceMap != null) {
                        populateResourceDetailsByPayload(payloadKeyResourceMap, resourceDetails);
                    }
                    break;
            }
        }
        return resourceDetails;
    }

    private static void populateResourceDetailsByHeader(BMap<BString, Object> headerResourceMap,
                                                        HashMap<String, BRecordType> resourceDetails) {
        headerResourceMap.values().forEach(value -> populateResourceDetails(resourceDetails, (ArrayValue) value));
    }

    private static void populateResourceDetailsByPayload(
            BMap<BString, BMap<BString, Object>> payloadKeyResourceMap,
            HashMap<String, BRecordType> resourceDetails) {
        payloadKeyResourceMap.values().forEach(mapByKey -> {
            mapByKey.values().forEach(value -> populateResourceDetails(resourceDetails, (ArrayValue) value));
        });
    }

    private static void populateResourceDetailsByHeaderAndPayload(
            BMap<BString, BMap<BString, BMap<BString, Object>>> headerAndPayloadKeyResourceMap,
            HashMap<String, BRecordType> resourceDetails) {
        headerAndPayloadKeyResourceMap.values().forEach(mapByHeader -> {
            mapByHeader.values().forEach(mapByKey -> {
                mapByKey.values().forEach(value -> populateResourceDetails(resourceDetails, (ArrayValue) value));
            });
        });
    }

    private static void populateResourceDetails(HashMap<String, BRecordType> resourceDetails,
                                                ArrayValue resourceDetailTuple) {
        String resourceName = resourceDetailTuple.getRefValue(0).toString();
        resourceDetails.put(resourceName,
                            (BRecordType) ((TypedescValue) resourceDetailTuple.getRefValue(1)).getDescribingType());
    }

    /**
     * Registers a WebSub Subscriber service.
     *
     * @param subscriberServiceListener the listener that the service has to be attached with
     * @param service                   the service to be registered
     */
    public static void registerWebSubSubscriberService(BObject subscriberServiceListener, BObject service) {
        BObject serviceEndpoint = (BObject) subscriberServiceListener.get(
                BStringUtils.fromString(LISTENER_SERVICE_ENDPOINT));
        WebSubServicesRegistry webSubServicesRegistry =
                (WebSubServicesRegistry) serviceEndpoint.getNativeData(WEBSUB_SERVICE_REGISTRY);
        webSubServicesRegistry.registerWebSubSubscriberService(service);
    }

    /**
     * Starts the registered WebSub Subscriber service.
     *
     * @param subscriberServiceListener the subscriber listener
     * @return an `error` if there is any error occurred during the listener start process
     */
    public static Object startWebSubSubscriberServiceEndpoint(BObject subscriberServiceListener) {
        BObject serviceEndpoint = (BObject) subscriberServiceListener.get(
                BStringUtils.fromString(WEBSUB_HTTP_ENDPOINT));
        ServerConnector serverConnector = (ServerConnector) serviceEndpoint.getNativeData(
                HttpConstants.HTTP_SERVER_CONNECTOR);
        //TODO: check if isStarted check is required
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
        WebSubServicesRegistry webSubServicesRegistry = (WebSubServicesRegistry) serviceEndpoint.getNativeData(
                WebSubSubscriberConstants.WEBSUB_SERVICE_REGISTRY);
        serverConnectorFuture.setHttpConnectorListener(
                new BallerinaWebSubConnectorListener(Scheduler.getStrand(), webSubServicesRegistry, serviceEndpoint
                        .getMapValue(HttpConstants.SERVICE_ENDPOINT_CONFIG)));
        serverConnectorFuture.setPortBindingEventListener(new HttpConnectorPortBindingListener());
        try {
            serverConnectorFuture.sync();
        } catch (Exception ex) {
            return WebSubUtils.createError(WebSubSubscriberConstants.WEBSUB_LISTENER_STARTUP_FAILURE,
                                           "failed to start server connector '" + serverConnector.getConnectorID() +
                                                   "': " + ex.getMessage());
        }
        return null;
    }

    /**
     * Set the hub and topic the service is subscribing, if the resource URL was specified as an annotation.
     *
     * @param subscriberServiceListener the subscriber listener
     * @param webSubServiceName         the name of the service for which subscription happened for a topic
     * @param topic                     the topic the subscription happened for
     */
    public static void setTopic(BObject subscriberServiceListener, BString webSubServiceName, BString topic) {
        BObject serviceEndpoint = (BObject) subscriberServiceListener.get(
                BStringUtils.fromString(WEBSUB_HTTP_ENDPOINT));
        WebSubServicesRegistry webSubServicesRegistry = ((WebSubServicesRegistry) serviceEndpoint.getNativeData(
                WEBSUB_SERVICE_REGISTRY));
        if (webSubServicesRegistry.getServicesMapHolder(DEFAULT_HOST) == null) {
            return;
        }
        Optional<HttpService> webSubHttpService =
                webSubServicesRegistry.getServicesByHost(DEFAULT_HOST).values().stream().filter(
                        httpService ->
                                webSubServiceName.getValue().equals(httpService.getBalService().getType().getName()))
                        .findFirst();

        HttpService httpService = webSubHttpService.orElse(null);
        if (httpService instanceof WebSubHttpService) {
            ((WebSubHttpService) httpService).setTopic(topic.getValue());
        }
    }

    /**
     * Retrieves the parameters specified for subscription as annotations and the callback URL to which notification
     * should happen for the services bound to the endpoint.
     *
     * @param subscriberServiceListener the subscriber listener
     * @return `map[]` array of maps containing subscription details for each service
     */
    @SuppressWarnings("unchecked")
    public static ArrayValue retrieveSubscriptionParameters(BObject subscriberServiceListener) {
        ArrayValue subscriptionDetailArray = (ArrayValue) BValueCreator.createArrayValue(mapArrayType);
        BObject serviceEndpoint = (BObject) subscriberServiceListener.get(
                BStringUtils.fromString(WEBSUB_HTTP_ENDPOINT));
        WebSubServicesRegistry webSubServicesRegistry = ((WebSubServicesRegistry) serviceEndpoint.getNativeData(
                WEBSUB_SERVICE_REGISTRY));
        if (webSubServicesRegistry.getServicesMapHolder(DEFAULT_HOST) == null) {
            return subscriptionDetailArray;
        }
        Object[] webSubHttpServices = webSubServicesRegistry.getServicesByHost(DEFAULT_HOST).values().toArray();

        for (int index = 0; index < webSubHttpServices.length; index++) {
            WebSubHttpService webSubHttpService = (WebSubHttpService) webSubHttpServices[index];
            BMap<BString, Object> subscriptionDetails = BValueCreator.createMapValue();
            BMap annotation = (BMap) webSubHttpService.getBalService().getType()
                    .getAnnotation(WEBSUB_PACKAGE_FULL_QUALIFIED_NAME, ANN_NAME_WEBSUB_SUBSCRIBER_SERVICE_CONFIG);

            subscriptionDetails.put(WEBSUB_SERVICE_NAME,
                                    BStringUtils.fromString(webSubHttpService.getBalService().getType().getName()));
            subscriptionDetails.put(ANN_WEBSUB_ATTR_SUBSCRIBE_ON_STARTUP,
                                    annotation.getBooleanValue(ANN_WEBSUB_ATTR_SUBSCRIBE_ON_STARTUP));

            if (annotation.containsKey(ANN_WEBSUB_ATTR_TARGET)) {
                subscriptionDetails.put(ANN_WEBSUB_ATTR_TARGET, annotation.get(ANN_WEBSUB_ATTR_TARGET));
            }

            if (annotation.containsKey(ANN_WEBSUB_ATTR_LEASE_SECONDS)) {
                subscriptionDetails.put(ANN_WEBSUB_ATTR_LEASE_SECONDS,
                                        annotation.getIntValue(ANN_WEBSUB_ATTR_LEASE_SECONDS));
            }

            if (annotation.containsKey(ANN_WEBSUB_ATTR_SECRET)) {
                subscriptionDetails.put(ANN_WEBSUB_ATTR_SECRET, annotation.getStringValue(ANN_WEBSUB_ATTR_SECRET));
            }

            subscriptionDetails.put(BStringUtils.fromString(ANN_WEBSUB_ATTR_EXPECT_INTENT_VERIFICATION),
                                    annotation.getBooleanValue(
                                            BStringUtils.fromString(ANN_WEBSUB_ATTR_EXPECT_INTENT_VERIFICATION)));

            if (annotation.containsKey(ANN_WEBSUB_ATTR_SUBSCRIPTION_PUBLISHER_CLIENT_CONFIG)) {
                BMap<BString, Object> publisherClientConfig = (BMap<BString, Object>)
                                annotation.get(ANN_WEBSUB_ATTR_SUBSCRIPTION_PUBLISHER_CLIENT_CONFIG);
                subscriptionDetails.put(ANN_WEBSUB_ATTR_SUBSCRIPTION_PUBLISHER_CLIENT_CONFIG, publisherClientConfig);
            }

            if (annotation.containsKey(ANN_WEBSUB_ATTR_SUBSCRIPTION_HUB_CLIENT_CONFIG)) {
                BMap<BString, Object> hubClientConfig =
                        (BMap<BString, Object>) annotation.get(ANN_WEBSUB_ATTR_SUBSCRIPTION_HUB_CLIENT_CONFIG);
                subscriptionDetails.put(ANN_WEBSUB_ATTR_SUBSCRIPTION_HUB_CLIENT_CONFIG, hubClientConfig);
            }

            String callback;

            if (annotation.containsKey(ANN_WEBSUB_ATTR_CALLBACK)) {
                callback = annotation.getStringValue(ANN_WEBSUB_ATTR_CALLBACK).getValue();
            } else {
                //TODO: intro methods to return host+port and change instead of using connector ID
                callback = webSubHttpService.getBasePath();
                BMap<BString, Object> serviceEndpointConfig = (BMap<BString, Object>) serviceEndpoint.get(
                        BStringUtils.fromString(SERVICE_ENDPOINT_CONFIG_NAME));
                long port = serviceEndpoint.getIntValue(BStringUtils.fromString(ENDPOINT_CONFIG_PORT));
                if (!serviceEndpointConfig.getStringValue(
                        BStringUtils.fromString(ENDPOINT_CONFIG_HOST)).getValue().isEmpty() && port != 0) {
                    callback = serviceEndpointConfig.getStringValue(BStringUtils.fromString(ENDPOINT_CONFIG_HOST))
                            + ":" + port + callback;
                } else {
                    callback = ((ServerConnector) serviceEndpoint.getNativeData(HTTP_SERVER_CONNECTOR))
                            .getConnectorID() + callback;
                }
                if (callback.startsWith(HTTP_DEFAULT_HOST)) {
                    callback = callback.replace(HTTP_DEFAULT_HOST, "localhost");
                }
                if (!callback.contains("://")) {
                    if (serviceEndpointConfig.get(ENDPOINT_CONFIG_SECURE_SOCKET_CONFIG) != null) {
                        //if secure socket is specified
                        callback = ("https://").concat(callback);
                    } else {
                        callback = ("http://").concat(callback);
                    }
                }
            }

            subscriptionDetails.put(ANN_WEBSUB_ATTR_CALLBACK, BStringUtils.fromString(callback));
            subscriptionDetailArray.add(index, subscriptionDetails);
        }
        return subscriptionDetailArray;
    }
}
