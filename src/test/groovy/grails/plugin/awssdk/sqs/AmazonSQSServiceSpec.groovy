package grails.plugin.awssdk.sqs

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(AmazonSQSService)
class AmazonSQSServiceSpec extends Specification {

    void setup() {
        // Mock collaborator
        service.client = Mock(AmazonSQSClient)
    }

    /*
     * Tests for createQueue(String queueName)
     */

    def "Create queue"() {
        when:
        String queueUrl = service.createQueue('someQueue')

        then:
        1 * service.client.createQueue(_) >> ['queueUrl': 'new.queue.url']
        queueUrl
        queueUrl == 'new.queue.url'
    }

    /*
     * Tests for deleteMessage(String queueUrl, String receiptHandle)
     */

    def "Delete message"() {
        when:
        service.deleteMessage('queueUrl', 'receiptHandle')

        then:
        1 * service.client.deleteMessage(_)
    }

    /*
     * Tests for listQueueUrls()
     */

    def "List queue urls"() {
        when:
        List queueUrls = service.listQueueUrls()

        then:
        1 * service.client.listQueues(_) >> ['queueUrls': ['queueUrl1', 'queueUrl2', 'queueUrl3']]
        queueUrls
        queueUrls.size() == 3
    }

    /*
     * Tests for getQueueAttributes(String queueUrl)
     */

    def "Get queue attributes"() {
        when:
        Map attributes = service.getQueueAttributes('queueUrl')

        then:
        1 * service.client.getQueueAttributes(_) >> {
            GetQueueAttributesResult attrResult = new GetQueueAttributesResult()
            attrResult.setAttributes(['attribute1': 'value1', 'attribute2': 'value2'])
            attrResult
        }
        attributes
        attributes.size() == 2
    }

    def "Get queue attributes service exception"() {
        given:
        service.queueUrls << 'queueUrl'

        when:
        Map attributes = service.getQueueAttributes('queueUrl')

        then:
        1 * service.client.getQueueAttributes(_) >> {
            AmazonServiceException exception = new AmazonServiceException('Error')
            exception.setErrorCode('AWS.SimpleQueueService.NonExistentQueue')
            throw exception
        }
        !attributes
        old(service.queueUrls.size() == 1)
        service.queueUrls.size() == 0
    }

    def "Get queue attributes client exception"() {
        when:
        Map attributes = service.getQueueAttributes('queueUrl')

        then:
        1 * service.client.getQueueAttributes(_) >> { throw new AmazonClientException('Error') }
        !attributes
    }

    /*
     * Tests for getQueueUrl(String jobName, String groupName)
     */

    def "Get queue url"() {
        when:
        String queueUrl = service.getQueueUrl('jobName_groupName')

        then:
        service.client.listQueues(_) >> ['queueUrls': ['queueUrl1', 'queueUrl2', 'queueUrl3']]
        service.client.createQueue(_) >> ['queueUrl': 'flo_groupName_jobName']
        service.client
        queueUrl == 'flo_groupName_jobName'
        old(service.queueUrls.size() == 0)
        service.queueUrls.size() == 4
        service.queueUrls.contains('flo_groupName_jobName')
    }

    /*
     * Tests for receiveMessages(String queueUrl, int maxNumberOfMessages = 0, int visibilityTimeout = 0, int waitTimeSeconds = 0)
     */

    def "Receive messages"() {
        when:
        List messages = service.receiveMessages('queueUrl', 1, 1, 1)

        then:
        1 * service.client.receiveMessage(_) >> ['messages': ['message1', 'message2']]
        messages
        messages.size() == 2
    }

    /*
     * Tests for sendMessage(String queueUrl, String messageBody)
     */

    def "Send messages"() {
        when:
        String messageId = service.sendMessage('queueUrl', 'messageBody')

        then:
        1 * service.client.sendMessage(_) >> ['messageId': 'msg_id']
        messageId == 'msg_id'
    }

}
