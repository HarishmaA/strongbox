package org.carlspring.strongbox.providers.io;

import org.carlspring.commons.encryption.EncryptionAlgorithmsEnum;
import org.carlspring.strongbox.config.Maven2LayoutProviderTestConfig;
import org.carlspring.strongbox.providers.layout.Maven2LayoutProvider;
import org.carlspring.strongbox.providers.repository.GroupRepositoryProvider;
import org.carlspring.strongbox.storage.repository.MutableRepository;
import org.carlspring.strongbox.storage.repository.RepositoryPolicyEnum;

import javax.inject.Inject;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.carlspring.strongbox.util.MessageDigestUtils.calculateChecksum;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Przemyslaw Fusik
 * @author Pablo Tirado
 */
@ExtendWith(SpringExtension.class)
@ActiveProfiles({ "MockedRestArtifactResolverTestConfig",
                  "test" })
@ContextConfiguration(classes = Maven2LayoutProviderTestConfig.class)
@Execution(CONCURRENT)
public class MavenMetadataExpirationSingleGroupCaseTest
        extends BaseMavenMetadataExpirationTest
{

    private static final String REPOSITORY_GROUP = "mvn-group-repo-snapshots";

    @Inject
    private GroupRepositoryProvider groupRepositoryProvider;

    @BeforeAll
    public static void cleanUp()
            throws Exception
    {
        cleanUp(getRepositoriesToClean(REPOSITORY_HOSTED,
                                       REPOSITORY_PROXY,
                                       REPOSITORY_LOCAL_SOURCE,
                                       REPOSITORY_GROUP));
    }

    private static Set<MutableRepository> getRepositoriesToClean(String... repositoryId)
    {
        Set<MutableRepository> repositories = new LinkedHashSet<>();

        Arrays.asList(repositoryId).forEach(
                r -> repositories.add(createRepositoryMock(STORAGE0, r, Maven2LayoutProvider.ALIAS))
        );
        return repositories;
    }

    @BeforeEach
    public void initialize()
            throws Exception
    {
        localSourceRepository = createRepository(STORAGE0,
                                                 REPOSITORY_LOCAL_SOURCE,
                                                 RepositoryPolicyEnum.SNAPSHOT.getPolicy(),
                                                 false);

        createRepository(STORAGE0,
                         REPOSITORY_HOSTED,
                         RepositoryPolicyEnum.SNAPSHOT.getPolicy(),
                         false);

        mockHostedRepositoryMetadataUpdate(localSourceRepository,
                                           REPOSITORY_HOSTED,
                                           REPOSITORY_LOCAL_SOURCE,
                                           versionLevelMetadata,
                                           artifactLevelMetadata);

        createProxyRepository(STORAGE0,
                              REPOSITORY_PROXY,
                              "http://localhost:48080/storages/" + STORAGE0 + "/" + REPOSITORY_HOSTED + "/");

        createGroup(STORAGE0, REPOSITORY_GROUP, REPOSITORY_PROXY);

        mockResolvingProxiedRemoteArtifactsToHostedRepository();
    }

    @Test
    public void groupRepositoryVersionLevelMetadataShouldBeRefreshedAsItsSingleProxySubrepository()
            throws Exception
    {
        final RepositoryPath hostedPath = resolvePath(REPOSITORY_HOSTED, true, "maven-metadata.xml");
        String sha1HostedPathChecksum = readChecksum(resolveSiblingChecksum(hostedPath, EncryptionAlgorithmsEnum.SHA1));
        assertNotNull(sha1HostedPathChecksum);

        final RepositoryPath proxyPath = resolvePath(REPOSITORY_PROXY, true, "maven-metadata.xml");
        final RepositoryPath groupPath = resolvePath(REPOSITORY_GROUP, true, "maven-metadata.xml");
        String sha1ProxyPathChecksum = readChecksum(resolveSiblingChecksum(proxyPath, EncryptionAlgorithmsEnum.SHA1));
        assertNull(sha1ProxyPathChecksum);

        assertFalse(RepositoryFiles.artifactExists(groupPath));

        RepositoryPath resolvedGroupPath = groupRepositoryProvider.fetchPath(groupPath);

        sha1ProxyPathChecksum = readChecksum(resolveSiblingChecksum(proxyPath, EncryptionAlgorithmsEnum.SHA1));
        assertNotNull(sha1ProxyPathChecksum);
        assertEquals(sha1ProxyPathChecksum, sha1HostedPathChecksum);

        String calculatedGroupPathChecksum = calculateChecksum(resolvedGroupPath,
                                                               EncryptionAlgorithmsEnum.SHA1.getAlgorithm());
        assertEquals(sha1ProxyPathChecksum, calculatedGroupPathChecksum);

        Files.setLastModifiedTime(proxyPath, oneHourAgo());

        sha1ProxyPathChecksum = readChecksum(resolveSiblingChecksum(proxyPath, EncryptionAlgorithmsEnum.SHA1));
        assertEquals(sha1ProxyPathChecksum, calculatedGroupPathChecksum);

        mockHostedRepositoryMetadataUpdate(localSourceRepository,
                                           REPOSITORY_HOSTED,
                                           REPOSITORY_LOCAL_SOURCE,
                                           versionLevelMetadata,
                                           artifactLevelMetadata);

        sha1HostedPathChecksum = readChecksum(resolveSiblingChecksum(hostedPath, EncryptionAlgorithmsEnum.SHA1));
        final String calculatedHostedPathChecksum = calculateChecksum(hostedPath,
                                                                      EncryptionAlgorithmsEnum.SHA1.getAlgorithm());
        assertEquals(sha1HostedPathChecksum, calculatedHostedPathChecksum);
        assertNotEquals(calculatedHostedPathChecksum, sha1ProxyPathChecksum);

        Files.setLastModifiedTime(proxyPath, oneHourAgo());

        resolvedGroupPath = groupRepositoryProvider.fetchPath(groupPath);
        sha1ProxyPathChecksum = readChecksum(resolveSiblingChecksum(proxyPath, EncryptionAlgorithmsEnum.SHA1));
        assertEquals(sha1ProxyPathChecksum, calculatedHostedPathChecksum);
        calculatedGroupPathChecksum = calculateChecksum(resolvedGroupPath,
                                                        EncryptionAlgorithmsEnum.SHA1.getAlgorithm());
        assertEquals(sha1ProxyPathChecksum, calculatedGroupPathChecksum);
    }

    @AfterEach
    public void removeRepositories()
            throws Exception
    {
        closeIndexersForRepository(STORAGE0, REPOSITORY_HOSTED);
        closeIndexersForRepository(STORAGE0, REPOSITORY_LOCAL_SOURCE);
        closeIndexersForRepository(STORAGE0, REPOSITORY_PROXY);
        closeIndexersForRepository(STORAGE0, REPOSITORY_GROUP);
        removeRepositories(getRepositoriesToClean());
        cleanUp();
    }

}
