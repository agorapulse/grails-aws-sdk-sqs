package grails.plugin.awssdk.sqs

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.Region
import com.amazonaws.regions.ServiceAbbreviations
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.*
import grails.core.GrailsApplication
import grails.plugin.awssdk.AwsClientUtil
import groovy.transform.Synchronized
import org.springframework.beans.factory.InitializingBean

class AmazonSQSService implements InitializingBean  {

    static SERVICE_NAME = ServiceAbbreviations.SQS

    GrailsApplication grailsApplication
    AmazonSQSClient client

    private List queueUrls = []

    void afterPropertiesSet() throws Exception {
        // Set region
        Region region = AwsClientUtil.buildRegion(config, serviceConfig)
        assert region?.isServiceSupported(SERVICE_NAME)

        // Create client
        def credentials = AwsClientUtil.buildCredentials(config, serviceConfig)
        ClientConfiguration configuration = AwsClientUtil.buildClientConfiguration(config, serviceConfig)
        client = new AmazonSQSClient(credentials, configuration)
                .withRegion(region)
    }

    /**
     *
     * @param queueName
     * @return
     */
    String createQueue(String queueName) {
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName)
        if (serviceConfig?.delaySeconds) {
            createQueueRequest.attributes['DelaySeconds'] = serviceConfig.delaySeconds.toString()
        }
        if (serviceConfig?.messageRetentionPeriod) {
            createQueueRequest.attributes['MessageRetentionPeriod'] = serviceConfig.messageRetentionPeriod.toString()
        }
        if (serviceConfig?.maximumMessageSize) {
            createQueueRequest.attributes['MaximumMessageSize'] = serviceConfig.maximumMessageSize.toString()
        }
        if (serviceConfig?.visibilityTimeout) {
            createQueueRequest.attributes['VisibilityTimeout'] = serviceConfig.visibilityTimeout.toString()
        }
        String queueUrl = client.createQueue(createQueueRequest).queueUrl
        log.debug "Queue created (queueUrl=$queueUrl)"
        queueUrl
    }

    /**
     *
     * @param queueUrl
     * @param receiptHandle
     */
    void deleteMessage(String queueUrl, String receiptHandle) {
        client.deleteMessage(new DeleteMessageRequest(queueUrl, receiptHandle))
        log.debug "Message deleted (queueUrl=$queueUrl)"
    }

    /**
     *
     * @return
     */
    List listQueueUrls() {
        if (!queueUrls) {
            loadQueueUrls()
        }
        // Return a clone to avoid java.util.ConcurrentModificationException
        queueUrls?.clone()
    }

    /**
     *
     * @param queueUrl
     * @return
     */
    Map getQueueAttributes(String queueUrl) {
        GetQueueAttributesRequest getQueueAttributesRequest = new GetQueueAttributesRequest(queueUrl).withAttributeNames(['All'])
        Map attributes = [:]
        try {
            attributes = client.getQueueAttributes(getQueueAttributesRequest).attributes
        } catch(AmazonServiceException exception) {
            if (exception.errorCode == 'AWS.SimpleQueueService.NonExistentQueue') {
                removeQueueUrl(queueUrl)
            }
            log.warn(exception)
        } catch (AmazonClientException exception) {
            log.warn(exception)
        }
        attributes
    }

    /**
     *
     * @param jobName
     * @param groupName
     * @return
     */
    String getQueueUrl(String queueName) {
        if (serviceConfig?.queueNamePrefix) {
            queueName = serviceConfig.queueNamePrefix + queueName
        }
        if (!queueUrls) {
            loadQueueUrls()
        }

        String queueUrl = queueUrls.find { String queueUrl -> if (queueUrl.find("/$queueName")) return queueUrl }
        if (!queueUrl) {
            queueUrl = createQueue(queueName)
            if (queueUrl) {
                addQueueUrl(queueUrl)
            }
        }
        queueUrl
    }

    /**
     *
     * @param queueUrl
     * @param maxNumberOfMessages
     * @param visibilityTimeout
     * @param waitTimeSeconds
     * @return
     */
    List receiveMessages(String queueUrl,
                         int maxNumberOfMessages = 0,
                         int visibilityTimeout = 0,
                         int waitTimeSeconds = 0) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl)
        if (maxNumberOfMessages) receiveMessageRequest.maxNumberOfMessages = maxNumberOfMessages
        if (visibilityTimeout) receiveMessageRequest.visibilityTimeout = visibilityTimeout
        if (waitTimeSeconds) receiveMessageRequest.waitTimeSeconds = waitTimeSeconds
        List messages = client.receiveMessage(receiveMessageRequest).messages
        log.debug "Messages received (count=${messages.size()})"
        messages
    }

    /**
     *
     * @param queueUrl
     * @param messageBody
     * @return
     */
    String sendMessage(String queueUrl,
                       String messageBody) {
        String messageId = client.sendMessage(new SendMessageRequest(queueUrl, messageBody)).messageId
        log.debug "Message sent (messageId=$messageId)"
        messageId
    }

    // PRIVATE

    def getConfig() {
        grailsApplication.config.grails?.plugin?.awssdk ?: grailsApplication.config.grails?.plugins?.awssdk
    }

    def getServiceConfig() {
        config[SERVICE_NAME]
    }

    @Synchronized
    private List addQueueUrl(String queueUrl) {
        queueUrls << queueUrl
    }

    @Synchronized
    private List loadQueueUrls() {
        ListQueuesRequest listQueuesRequest = new ListQueuesRequest()
        if (serviceConfig?.queueNamePrefix) {
            listQueuesRequest.queueNamePrefix = serviceConfig.queueNamePrefix
        }
        queueUrls = client.listQueues(listQueuesRequest).queueUrls
    }

    @Synchronized
    private List removeQueueUrl(String queueUrl) {
        queueUrls = queueUrls.findAll { it != queueUrl }
    }

}
