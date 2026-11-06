/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.bulkimport.service;

import jakarta.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import org.apache.fineract.infrastructure.bulkimport.data.BulkImportEvent;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.infrastructure.bulkimport.data.ImportData;
import org.apache.fineract.infrastructure.bulkimport.domain.ImportDocument;
import org.apache.fineract.infrastructure.bulkimport.domain.ImportDocumentRepository;
import org.apache.fineract.infrastructure.bulkimport.importhandler.ImportHandlerUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepository;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformService;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class BulkImportWorkbookServiceImpl implements BulkImportWorkbookService {

    private static final Logger LOG = LoggerFactory.getLogger(BulkImportWorkbookServiceImpl.class);
    private final ApplicationContext applicationContext;
    private final PlatformSecurityContext securityContext;
    private final DocumentWritePlatformService documentWritePlatformService;
    private final DocumentRepository documentRepository;
    private final ImportDocumentRepository importDocumentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public BulkImportWorkbookServiceImpl(final ApplicationContext applicationContext, final PlatformSecurityContext securityContext,
            final DocumentWritePlatformService documentWritePlatformService, final DocumentRepository documentRepository,
            final ImportDocumentRepository importDocumentRepository, final JdbcTemplate jdbcTemplate) {
        this.applicationContext = applicationContext;
        this.securityContext = securityContext;
        this.documentWritePlatformService = documentWritePlatformService;
        this.documentRepository = documentRepository;
        this.importDocumentRepository = importDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long importWorkbook(String entity, InputStream inputStream, FormDataContentDisposition fileDetail, final String locale,
            final String dateFormat) {
        try {
            if (entity == null || inputStream == null || fileDetail == null || locale == null || dateFormat == null) {
                throw new GeneralPlatformDomainRuleException("error.msg.null", "One or more of the given parameters not found");
            }
            final GlobalEntityType entityType = resolveEntityType(entity);
            this.securityContext.authenticatedUser().validateHasCreatePermission(createPermissionResource(entityType));

            final byte[] bytes = readWorkbook(inputStream);
            final InputStream clonedInputStream = new ByteArrayInputStream(bytes);
            final BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(bytes));
            final Tika tika = new Tika();
            final TikaInputStream tikaInputStream = TikaInputStream.get(bis);
            final String fileType = tika.detect(tikaInputStream);
            if (!fileType.contains("msoffice") && !fileType.contains("application/vnd.ms-excel")) {
                throw new GeneralPlatformDomainRuleException("error.msg.invalid.file.extension",
                        "Uploaded file extension is not recognized.");
            }
            final Workbook workbook = new HSSFWorkbook(clonedInputStream);
            return publishEvent(0, fileDetail, bis, entityType, workbook, locale, dateFormat);
        } catch (IOException e) {
            LOG.error("Problem occurred in importWorkbook function", e);
            throw new GeneralPlatformDomainRuleException("error.msg.io.exception",
                    "IO exception occured with " + fileDetail.getFileName() + " " + e.getMessage(), e);

        }
    }

    private static GlobalEntityType resolveEntityType(final String entity) {
        try {
            final GlobalEntityType entityType = GlobalEntityType.valueOf(entity.trim().toUpperCase(Locale.ROOT));
            createPermissionResource(entityType);
            return entityType;
        } catch (IllegalArgumentException exception) {
            throw new GeneralPlatformDomainRuleException("error.msg.unable.to.find.resource", "Unable to find requested resource");
        }
    }

    private static String createPermissionResource(final GlobalEntityType entityType) {
        return switch (entityType) {
            case CLIENTS_PERSON, CLIENTS_ENTITY -> "CLIENT";
            case CENTERS -> "CENTER";
            case GROUPS -> "GROUP";
            case LOANS, LOAN_TRANSACTIONS -> "LOAN";
            case GUARANTORS -> "GUARANTOR";
            case OFFICES -> "OFFICE";
            case CHART_OF_ACCOUNTS -> "GLACCOUNT";
            case GL_JOURNAL_ENTRIES -> "JOURNALENTRY";
            case STAFF -> "STAFF";
            case SHARE_ACCOUNTS -> "SHAREACCOUNT";
            case SAVINGS_ACCOUNT, SAVINGS_TRANSACTIONS -> "SAVINGSACCOUNT";
            case RECURRING_DEPOSIT_ACCOUNTS, RECURRING_DEPOSIT_ACCOUNTS_TRANSACTIONS -> "RECURRINGDEPOSITACCOUNT";
            case FIXED_DEPOSIT_ACCOUNTS, FIXED_DEPOSIT_TRANSACTIONS -> "FIXEDDEPOSITACCOUNT";
            case USERS -> "USER";
            default -> throw new IllegalArgumentException("Unsupported import entity: " + entityType);
        };
    }

    private static byte[] readWorkbook(final InputStream inputStream) throws IOException {
        final long maxSize = ContentRepository.MAX_FILE_UPLOAD_SIZE_IN_MB * 1024L * 1024L;
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        long totalSize = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, maxSize - totalSize + 1))) != -1) {
            totalSize += bytesRead;
            if (totalSize > maxSize) {
                throw new GeneralPlatformDomainRuleException("error.msg.document.file.too.big",
                        "Uploaded workbook exceeds the maximum allowed size of " + ContentRepository.MAX_FILE_UPLOAD_SIZE_IN_MB + " MB.");
            }
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }

    private Long publishEvent(final Integer primaryColumn, final FormDataContentDisposition fileDetail,
            final InputStream clonedInputStreamWorkbook, final GlobalEntityType entityType, final Workbook workbook, final String locale,
            final String dateFormat) {

        final String fileName = fileDetail.getFileName();

        final Long documentId = this.documentWritePlatformService.createInternalDocument(
                DocumentWritePlatformServiceJpaRepositoryImpl.DocumentManagementEntity.IMPORT.name(),
                this.securityContext.authenticatedUser().getId(), null, clonedInputStreamWorkbook,
                URLConnection.guessContentTypeFromName(fileName), fileName, null, fileName);
        final Document document = this.documentRepository.findById(documentId).orElse(null);

        final ImportDocument importDocument = ImportDocument.instance(document, DateUtils.getLocalDateTimeOfTenant(), entityType.getValue(),
                this.securityContext.authenticatedUser(), ImportHandlerUtils.getNumberOfRows(workbook.getSheetAt(0), primaryColumn));
        this.importDocumentRepository.saveAndFlush(importDocument);
        BulkImportEvent event = BulkImportEvent.instance(this, workbook, importDocument.getId(), locale, dateFormat,
                ThreadLocalContextUtil.getContext());
        applicationContext.publishEvent(event);
        return importDocument.getId();
    }

    @Override
    public Collection<ImportData> getImports(GlobalEntityType type) {
        this.securityContext.authenticatedUser();

        final ImportMapper rm = new ImportMapper();
        final String sql = "select " + rm.schema() + " order by i.id desc";

        return this.jdbcTemplate.query(sql, rm, new Object[] { type.getValue() }); // NOSONAR
    }

    private static final class ImportMapper implements RowMapper<ImportData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();
            sql.append("i.id as id, i.document_id as documentId, d.name as name, i.import_time as importTime, i.end_time as endTime, ")
                    .append("i.completed as completed, i.total_records as totalRecords, i.success_count as successCount, ")
                    .append("i.failure_count as failureCount, i.createdby_id as createdBy ")
                    .append("from m_import_document i inner join m_document d on i.document_id=d.id ").append("where i.entity_type= ? ");
            return sql.toString();
        }

        @Override
        public ImportData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final Long documentId = rs.getLong("documentId");
            final String name = rs.getString("name");
            final LocalDate importTime = JdbcSupport.getLocalDate(rs, "importTime");
            final LocalDate endTime = JdbcSupport.getLocalDate(rs, "endTime");
            final Boolean completed = rs.getBoolean("completed");
            final Integer totalRecords = JdbcSupport.getInteger(rs, "totalRecords");
            final Integer successCount = JdbcSupport.getInteger(rs, "successCount");
            final Integer failureCount = JdbcSupport.getInteger(rs, "failureCount");
            final Long createdBy = rs.getLong("createdBy");

            return ImportData.instance(id, documentId, importTime, endTime, completed, name, createdBy, totalRecords, successCount,
                    failureCount);
        }
    }

    @Override
    public DocumentData getOutputTemplateLocation(String importDocumentId) {
        this.securityContext.authenticatedUser();
        final ImportTemplateLocationMapper importTemplateLocationMapper = new ImportTemplateLocationMapper();
        final String sql = "select " + importTemplateLocationMapper.schema();

        return this.jdbcTemplate.queryForObject(sql, importTemplateLocationMapper, new Object[] { Integer.parseInt(importDocumentId) }); // NOSONAR
    }

    @Override
    public Response getOutputTemplate(String importDocumentId) {
        this.securityContext.authenticatedUser();
        final ImportTemplateLocationMapper importTemplateLocationMapper = new ImportTemplateLocationMapper();
        final String sql = "select " + importTemplateLocationMapper.schema();
        DocumentData documentData = this.jdbcTemplate.queryForObject(sql, importTemplateLocationMapper, new Object[] { importDocumentId }); // NOSONAR
        return buildResponse(documentData);
    }

    private Response buildResponse(DocumentData documentData) {
        String fileName = "Output" + documentData.getFileName();
        String fileLocation = documentData.getLocation();
        File file = new File(fileLocation);
        final Response.ResponseBuilder response = Response.ok(file);
        response.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.header("Content-Type", "application/vnd.ms-excel");
        return response.build();
    }

    private static final class ImportTemplateLocationMapper implements RowMapper<DocumentData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();
            sql.append("d.location,d.file_name ").append("from m_import_document i inner join m_document d on i.document_id=d.id ")
                    .append("where i.id= ? ");
            return sql.toString();
        }

        @Override
        public DocumentData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final String location = rs.getString("location");
            final String fileName = rs.getString("file_name");
            return new DocumentData(null, null, null, null, fileName, null, null, location, null, null);
        }
    }
}
