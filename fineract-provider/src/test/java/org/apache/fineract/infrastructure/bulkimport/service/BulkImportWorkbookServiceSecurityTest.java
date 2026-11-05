package org.apache.fineract.infrastructure.bulkimport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.fineract.infrastructure.bulkimport.domain.ImportDocumentRepository;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepository;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BulkImportWorkbookServiceSecurityTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private DocumentWritePlatformService documentWritePlatformService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ImportDocumentRepository importDocumentRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AppUser authenticatedUser;
    @InjectMocks
    private BulkImportWorkbookServiceImpl underTest;

    @BeforeEach
    void setUp() {
        when(securityContext.authenticatedUser()).thenReturn(authenticatedUser);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void rejectsWorkbookWithoutCreatePermissionBeforeReadingIt() {
        final AtomicBoolean inputRead = new AtomicBoolean();
        final InputStream inputStream = failingInputStream(inputRead);
        doThrow(new NoAuthorizationException("missing create permission")).when(authenticatedUser)
                .validateHasCreatePermission("SAVINGSACCOUNT");

        assertThrows(NoAuthorizationException.class,
                () -> underTest.importWorkbook("SAVINGS_ACCOUNT", inputStream, mock(FormDataContentDisposition.class), "en", "dd-MM-yyyy"));

        assertFalse(inputRead.get());
    }

    @Test
    void checksCreatePermissionForAuthorizedWorkbookBeforeReadingIt() {
        final AtomicBoolean inputRead = new AtomicBoolean();

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.importWorkbook("SAVINGS_ACCOUNT", failingInputStream(inputRead), mock(FormDataContentDisposition.class),
                        "en", "dd-MM-yyyy"));

        assertEquals("error.msg.io.exception", exception.getGlobalisationMessageCode());
        assertTrue(inputRead.get());
        verify(authenticatedUser).validateHasCreatePermission("SAVINGSACCOUNT");
    }

    @Test
    void rejectsWorkbookLargerThanConfiguredLimitWithoutReadingRemainder() {
        final int maxSize = Math.toIntExact(ContentRepository.MAX_FILE_UPLOAD_SIZE_IN_MB * 1024L * 1024L);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[maxSize + 8192]);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.importWorkbook("SAVINGS_ACCOUNT", inputStream, mock(FormDataContentDisposition.class), "en", "dd-MM-yyyy"));

        assertEquals("error.msg.document.file.too.big", exception.getGlobalisationMessageCode());
        assertEquals(8191, inputStream.available());
    }

    @Test
    void importsAuthorizedWorkbookWithinConfiguredLimit() throws IOException {
        final byte[] workbookBytes;
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.createSheet();
            workbook.write(outputStream);
            workbookBytes = outputStream.toByteArray();
        }
        final FormDataContentDisposition fileDetail = mock(FormDataContentDisposition.class);
        final Document document = mock(Document.class);
        when(fileDetail.getFileName()).thenReturn("savings.xls");
        when(authenticatedUser.getId()).thenReturn(1L);
        when(documentWritePlatformService.createInternalDocument(anyString(), eq(1L), nullable(Long.class), any(InputStream.class),
                anyString(), anyString(), nullable(String.class), anyString())).thenReturn(1L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        underTest.importWorkbook("SAVINGS_ACCOUNT", new ByteArrayInputStream(workbookBytes), fileDetail, "en", "dd-MM-yyyy");

        verify(authenticatedUser).validateHasCreatePermission("SAVINGSACCOUNT");
        verify(documentWritePlatformService).createInternalDocument(anyString(), eq(1L), nullable(Long.class), any(InputStream.class),
                anyString(), anyString(), nullable(String.class), anyString());
    }

    private static InputStream failingInputStream(final AtomicBoolean inputRead) {
        return new InputStream() {

            @Override
            public int read() throws IOException {
                inputRead.set(true);
                throw new IOException("test read");
            }
        };
    }
}
