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
import groovy.util.logging.Log4j
import org.springframework.beans.factory.InitializingBean

@Log4j
class AmazonSQSService implements InitializingBean  {

    static SERVICE_NAME = ServiceAbbreviations.SQS
    GrailsApplication grailsApplication
    AmazonSQSClient client
    private String defaultQueueName = ''

    private Map queueUrlByNames = [:]
    static private String getQueueNameFromUrl(String queueUrl) {
        queueUrl.tokenize('/').last()
    }

    void afterPropertiesSet() throws Exception {
        // Set region
        Region region = AwsClientUtil.buildRegion(config, serviceConfig)
        assert region?.isServiceSupported(SERVICE_NAME)

        // Create client
        def credentials = AwsClientUtil.buildCredentials(config, serviceConfig)
        ClientConfiguration configuration = AwsClientUtil.buildClientConfiguration(config, serviceConfig)
        client = new AmazonSQSClient(credentials, configuration)
                .withRegion(region)

        defaultQueueName = serviceConfig?.queue ?: ''
    }

    protected void init(String queueName) {
        defaultQueueName = queueName
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
        addQueue(queueUrl)
        queueUrl
    }

    /**
     *
     * @param queueUrl
     * @param receiptHandle
     */
    void deleteMessage(String queueName,
                       String receiptHandle) {
        String queueUrl = getQueueUrl(queueName)
        assert queueUrl, "Queue ${queueName} not found"

        client.deleteMessage(new DeleteMessageRequest(queueUrl, receiptHandle))
        log.debug "Message deleted (queueUrl=$queueUrl)"
    }

    /**
     *
     * @param receiptHandle
     */

    void deleteMessage(String receiptHandle) {
        assertDefaultQueueName()
        deleteMessage(defaultQueueName, receiptHandle)
    }

    /**
     *
     * @param queueName
     */
    void deleteQueue(String queueName) {
        String queueUrl = getQueueUrl(queueName)
        assert queueUrl, "Queue ${queueName} not found"

        client.deleteQueue(queueUrl)
        removeQueue(queueUrl)
    }

    /**
     *
     * @param queueName
     * @return
     */
    Map getQueueAttributes(String queueName) {
        String queueUrl = getQueueUrl(queueName)
        assert queueUrl, "Queue ${queueName} not found"

        GetQueueAttributesRequest getQueueAttributesRequest = new GetQueueAttributesRequest(queueUrl).withAttributeNames(['All'])
        Map attributes = [:]
        try {
            attributes = client.getQueueAttributes(getQueueAttributesRequest).attributes
        } catch(AmazonServiceException exception) {
            if (exception.errorCode == 'AWS.SimpleQueueService.NonExistentQueue') {
                removeQueue(queueUrl)
            }
            log.warn(exception)
        } catch (AmazonClientException exception) {
            log.warn(exception)
        }
        attributes
    }

    /**
     *
     * @return
     */
    Map getQueueAttributes() {
        assertDefaultQueueName()
        getQueueAttributes(defaultQueueName)
    }

    /**
     *
     * @param jobName
     * @param groupName
     * @param autoCreate
     * @return
     */
    String getQueueUrl(String queueName,
                       boolean autoCreate = false) {
        if (serviceConfig?.queueNamePrefix) {
            queueName = serviceConfig.queueNamePrefix + queueName
        }
        if (!queueUrlByNames) {
            loadQueues()
        }

        String queueUrl = queueUrlByNames[queueName]
        if (!queueUrl && autoCreate) {
            queueUrl = createQueue(queueName)
        }
        queueUrl
    }

    /**
     *
     * @param autoCreate
     * @return
     */
    String getQueueUrl(boolean autoCreate = false) {
        assertDefaultQueueName()
        getQueueUrl(defaultQueueName)
    }

    /**
     *
     * @param reload
     * @return
     */
    List<String> listQueueNames(boolean reload = false) {
        if (!queueUrlByNames || reload) {
            loadQueues()
        }
        queueUrlByNames?.keySet().sort()
    }

    /**
     *
     * @param reload
     * @return
     */
    List<String> listQueueUrls(boolean reload = false) {
        if (!queueUrlByNames || reload) {
            loadQueues()
        }
        queueUrlByNames?.values().sort()
    }

    /**
     *
     * @param queueName
     * @param maxNumberOfMessages
     * @param visibilityTimeout
     * @param waitTimeSeconds
     * @return
     */
    List receiveMessages(String queueName,
                         int maxNumberOfMessages = 1,
                         int visibilityTimeout = 0,
                         int waitTimeSeconds = 0) {
        String queueUrl = getQueueUrl(queueName)
        assert queueUrl, "Queue ${queueName} not found"

        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl)
        if (maxNumberOfMessages) {
            receiveMessageRequest.maxNumberOfMessages = maxNumberOfMessages
        }
        if (visibilityTimeout) {
            receiveMessageRequest.visibilityTimeout = visibilityTimeout
        }
        if (waitTimeSeconds) {
            receiveMessageRequest.waitTimeSeconds = waitTimeSeconds
        }
        List messages = client.receiveMessage(receiveMessageRequest).messages
        log.debug "Messages received (count=${messages.size()})"
        messages
    }

    /**
     *
     * @param maxNumberOfMessages
     * @param visibilityTimeout
     * @param waitTimeSeconds
     * @return
     */
    List receiveMessages(int maxNumberOfMessages = 1,
                         int visibilityTimeout = 0,
                         int waitTimeSeconds = 0) {
        assertDefaultQueueName()
        receiveMessages(maxNumberOfMessages, visibilityTimeout, waitTimeSeconds)
    }

    /**
     *
     * @param queueUrl
     * @param messageBody
     * @return
     */
    String sendMessage(String queueName,
                       String messageBody) {
        String queueUrl = getQueueUrl(queueName)
        assert queueUrl, "Queue ${queueName} not found"

        String messageId = client.sendMessage(new SendMessageRequest(queueUrl, messageBody)).messageId
        log.debug "Message sent (messageId=$messageId)"
        messageId
    }

    /**
     *
     * @param messageBody
     * @return
     */
    String sendMessage(String messageBody) {
        assertDefaultQueueName()
        sendMessage(defaultQueueName, messageBody)
    }

    // PRIVATE

    boolean assertDefaultQueueName() {
        assert defaultQueueName, "Default queue must be defined"
    }

    def getConfig() {
        grailsApplication.config.grails?.plugin?.awssdk ?: grailsApplication.config.grails?.plugins?.awssdk
    }

    def getServiceConfig() {
        config[SERVICE_NAME]
    }

    @Synchronized
    private void addQueue(String queueUrl) {
        assert queueUrl, "Invalid queueUrl"
        queueUrlByNames[getQueueNameFromUrl(queueUrl)] = queueUrl
    }

    @Synchronized
    private void loadQueues() {
        ListQueuesRequest listQueuesRequest = new ListQueuesRequest()
        if (serviceConfig?.queueNamePrefix) {
            listQueuesRequest.queueNamePrefix = serviceConfig.queueNamePrefix
        }
        List queueUrls = client.listQueues(listQueuesRequest).queueUrls
        queueUrlByNames = queueUrls?.collectEntries { queueUrl ->
            [getQueueNameFromUrl(queueUrl), queueUrl]
        }

    }

    @Synchronized
    private void removeQueue(String queueUrl) {
        assert queueUrl, "Invalid queueUrl"
        queueUrlByNames.remove(getQueueNameFromUrl(queueUrl))
    }

}
