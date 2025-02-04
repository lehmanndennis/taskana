package pro.taskana.rest;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.hateoas.hal.Jackson2HalModule;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import pro.taskana.rest.resource.WorkbasketDefinitionResource;
/**
 * Integration tests for WorkbasketDefinitionController.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = RestConfiguration.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class WorkbasketDefinitionControllerIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationController.class);
    private String server = "http://127.0.0.1:";
    private RestTemplate template;
    private HttpEntity<String> request;
    private HttpHeaders headers = new HttpHeaders();
    private ObjectMapper objMapper = new ObjectMapper();
    @LocalServerPort
    private int port;

    @Before
    public void before() {
        LOGGER.debug("before");
        template = getRestTemplate();
        headers.add("Authorization", "Basic dGVhbWxlYWRfMTp0ZWFtbGVhZF8x");
        request = new HttpEntity<String>(headers);
    }

    @Test
    public void testExportWorkbasketFromDomain() {
        ResponseEntity<List<WorkbasketDefinitionResource>> response = template.exchange(
            server + port + "/v1/workbasket-definitions?domain=DOMAIN_A", HttpMethod.GET, request,
            new ParameterizedTypeReference<List<WorkbasketDefinitionResource>>() {

            });
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat(response.getBody().get(0), instanceOf(WorkbasketDefinitionResource.class));

        boolean allAuthorizationsAreEmpty = true, allDistributionTargetsAreEmpty = true;
        for (WorkbasketDefinitionResource workbasketDefinition : response.getBody()) {
            if (allAuthorizationsAreEmpty && !workbasketDefinition.getAuthorizations().isEmpty()) {
                allAuthorizationsAreEmpty = false;
            }
            if (allDistributionTargetsAreEmpty && !workbasketDefinition.getDistributionTargets().isEmpty()) {
                allDistributionTargetsAreEmpty = false;
            }
            if (!allAuthorizationsAreEmpty && !allDistributionTargetsAreEmpty) {
                break;
            }
        }
        assertFalse(allDistributionTargetsAreEmpty);
        assertFalse(allAuthorizationsAreEmpty);
    }

    @Test
    public void testExportWorkbasketsFromWrongDomain() {
        ResponseEntity<List<WorkbasketDefinitionResource>> response = template.exchange(
            server + port + "/v1/workbasket-definitions?domain=wrongDomain",
            HttpMethod.GET, request, new ParameterizedTypeReference<List<WorkbasketDefinitionResource>>() {

            });
        assertEquals(0, response.getBody().size());
    }

    @Test
    public void testImportWorkbasket() throws IOException {
        ResponseEntity<List<WorkbasketDefinitionResource>> response = template.exchange(
            server + port + "/v1/workbasket-definitions?domain=DOMAIN_A",
            HttpMethod.GET, request, new ParameterizedTypeReference<List<WorkbasketDefinitionResource>>() {

            });

        List<String> list = new ArrayList<>();
        list.add(objMapper.writeValueAsString(response.getBody().get(0)));
        ResponseEntity<String> responseImport = importRequest(list);
        assertEquals(HttpStatus.OK, responseImport.getStatusCode());
    }

    @Test
    public void testNoErrorWhenImportWithSameIdButDifferentKeyAndDomain()
        throws IOException {
        ResponseEntity<List<WorkbasketDefinitionResource>> response = template.exchange(
            server + port + "/v1/workbasket-definitions?domain=DOMAIN_A",
            HttpMethod.GET, request, new ParameterizedTypeReference<List<WorkbasketDefinitionResource>>() {

            });

        List<String> list = new ArrayList<>();
        WorkbasketDefinitionResource wbDef = response.getBody().get(0);
        list.add(objMapper.writeValueAsString(wbDef));
        wbDef.getWorkbasket().setKey("new Key for this WB");
        list.add(objMapper.writeValueAsString(wbDef));
        ResponseEntity<String> responseImport = importRequest(list);
        assertEquals(HttpStatus.OK, responseImport.getStatusCode());
    }

    private ResponseEntity<String> importRequest(List<String> clList) throws IOException {
        File tmpFile = File.createTempFile("test", ".tmp");
        FileWriter writer = new FileWriter(tmpFile);
        writer.write(clList.toString());
        writer.close();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        body.add("file", new FileSystemResource(tmpFile));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        String serverUrl = server + port + "/v1/workbasket-definitions";
        RestTemplate restTemplate = new RestTemplate();

        return restTemplate.postForEntity(serverUrl, requestEntity, String.class);
    }

    /**
     * Return a REST template which is capable of dealing with responses in HAL format.
     *
     * @return RestTemplate
     */
    private RestTemplate getRestTemplate() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new Jackson2HalModule());

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(MediaType.parseMediaTypes("application/haljson,*/*"));
        converter.setObjectMapper(mapper);

        return new RestTemplate(Collections.<HttpMessageConverter<?>>singletonList(converter));
    }
}
