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
package org.apache.fineract.integrationtests.bulkimport.importhandler.loan;

import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.client.models.PaymentTypeRequest;
import org.apache.fineract.client.models.PostPaymentTypesResponse;
import org.apache.fineract.infrastructure.bulkimport.constants.LoanConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeDomain;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.funds.FundsHelper;
import org.apache.fineract.integrationtests.common.funds.FundsResourceHandler;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Order(3)
public class LoanImportHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoanImportHandlerTest.class);
    private static final String CREATE_CLIENT_URL = "/fineract-provider/api/v1/clients?" + Utils.TENANT_IDENTIFIER;
    public static final String DATE_FORMAT = "dd MMMM yyyy";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private PaymentTypeHelper paymentTypeHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.paymentTypeHelper = new PaymentTypeHelper();
    }

    @Test
    public void testLoanImport() throws InterruptedException, IOException, ParseException {
        requestSpec.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        // in order to populate helper sheets
        OfficeHelper officeHelper = new OfficeHelper(requestSpec, responseSpec);
        Integer outcome_office_creation = officeHelper.createOffice("02 May 2000");
        Assertions.assertNotNull(outcome_office_creation, "Could not create office");
        Integer outOfScopeOfficeId = officeHelper.createOffice("02 May 2000");
        Assertions.assertNotNull(outOfScopeOfficeId, "Could not create second office");

        OfficeDomain office = officeHelper.retrieveOfficeByID(outcome_office_creation);
        Assertions.assertNotNull(office, "Could not retrieve created office");

        String firstName = Utils.randomStringGenerator("Client_FirstName_", 5);
        String lastName = Utils.randomStringGenerator("Client_LastName_", 4);
        String externalId = UUID.randomUUID().toString();

        final HashMap<String, Object> clientMap = new HashMap<>();
        clientMap.put("officeId", outcome_office_creation.toString());
        clientMap.put("firstname", firstName);
        clientMap.put("lastname", lastName);
        clientMap.put("externalId", externalId);
        clientMap.put("dateFormat", DATE_FORMAT);
        clientMap.put("legalFormId", 1);
        clientMap.put("locale", "en");
        clientMap.put("active", "true");
        clientMap.put("activationDate", "04 March 2011");

        Integer outcome_client_creation = Utils.performServerPost(requestSpec, responseSpec, CREATE_CLIENT_URL,
                new Gson().toJson(clientMap), "clientId");
        Assertions.assertNotNull(outcome_client_creation, "Could not create client");

        List<HashMap> collaterals = new ArrayList<>();
        HashMap<String, String> collateralHashMap = new HashMap<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                String.valueOf(outcome_client_creation), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        collateralHashMap.put("clientCollateralId", collateralId.toString());
        collateralHashMap.put("quantity", "1");
        collaterals.add(collateralHashMap);

        ImportedClient sameOfficeImportClient = createImportedClient(outcome_office_creation, collateralId);
        ImportedClient outOfScopeImportClient = createImportedClient(outOfScopeOfficeId, collateralId);

        final String disbursementChargeJsonString = ChargesHelper.getLoanDisbursementJSON();

        final Integer disbursementChargeId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec, disbursementChargeJsonString);

        final JsonPath disbursementChargeJSON = JsonPath.from(disbursementChargeJsonString);

        Assertions.assertNotNull(disbursementChargeId, "Could not create charge");

        // in order to populate helper sheets
        Integer outcome_group_creation = GroupHelper.createGroup(requestSpec, responseSpec, true);
        Assertions.assertNotNull(outcome_group_creation, "Could not create group");

        // in order to populate helper sheets
        Integer outcome_staff_creation = StaffHelper.createStaff(requestSpec, responseSpec);
        Assertions.assertNotNull(outcome_staff_creation, "Could not create staff");

        Map<String, Object> staffMap = StaffHelper.getStaff(requestSpec, responseSpec, outcome_staff_creation);
        Assertions.assertNotNull(staffMap, "Could not retrieve created staff");

        Integer scopedRoleId = RolesHelper.createRole(requestSpec, responseSpec);
        RolesHelper.addPermissionsToRole(requestSpec, responseSpec, scopedRoleId,
                Map.of("ALL_FUNCTIONS_READ", true, "CREATE_LOAN", true, "APPROVE_LOAN", true, "DISBURSE_LOAN", true, "REPAYMENT_LOAN", true,
                        "CREATE_DOCUMENT", true, "READ_DOCUMENT", true, "UPDATE_DOCUMENT", true));
        HashMap<String, Object> scopedStaffRequest = new Gson().fromJson(StaffHelper.createStaffAsJSON(), HashMap.class);
        scopedStaffRequest.put("officeId", outcome_office_creation);
        Integer scopedStaffId = (Integer) StaffHelper.createStaffWithJson(requestSpec, responseSpec, new Gson().toJson(scopedStaffRequest))
                .get("resourceId");
        Map<String, Object> scopedStaff = StaffHelper.getStaff(requestSpec, responseSpec, scopedStaffId);
        String scopedUsername = Utils.uniqueRandomStringGenerator("loan_import_", 8);
        String scopedPassword = "A1b2c3d4e5f$";
        HashMap<String, Object> scopedUserRequest = new HashMap<>();
        scopedUserRequest.put("username", scopedUsername);
        scopedUserRequest.put("firstname", "Loan");
        scopedUserRequest.put("lastname", "Importer");
        scopedUserRequest.put("email", scopedUsername + "@example.com");
        scopedUserRequest.put("officeId", String.valueOf(outcome_office_creation));
        scopedUserRequest.put("staffId", String.valueOf(scopedStaffId));
        scopedUserRequest.put("roles", List.of(String.valueOf(scopedRoleId)));
        scopedUserRequest.put("sendPasswordToEmail", false);
        scopedUserRequest.put("password", scopedPassword);
        scopedUserRequest.put("repeatPassword", scopedPassword);
        io.restassured.RestAssured.given().spec(requestSpec).contentType(ContentType.JSON).body(new Gson().toJson(scopedUserRequest))
                .post("/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER).then().spec(responseSpec);
        RequestSpecification scopedRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build().header("Authorization",
                "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(scopedUsername, scopedPassword));
        LoanTransactionHelper scopedLoanTransactionHelper = new LoanTransactionHelper(scopedRequestSpec, responseSpec);

        LoanTransactionHelper ltHelper = new LoanTransactionHelper(requestSpec, responseSpec);
        LoanProductTestBuilder loanProductTestBuilder = new LoanProductTestBuilder();
        String jsonLoanProduct = loanProductTestBuilder.build(null);
        Integer outcome_lp_creation = ltHelper.getLoanProductId(jsonLoanProduct);
        Assertions.assertNotNull(outcome_lp_creation, "Could not create Loan Product");

        String loanProductStr = ltHelper.getLoanProductDetails(requestSpec, responseSpec, outcome_lp_creation);
        Assertions.assertNotNull("Could not get created Loan Product", loanProductStr);
        JsonPath loanProductJson = JsonPath.from(loanProductStr);

        String fundName = Utils.uniqueRandomStringGenerator("", 9);
        FundsHelper fh = FundsHelper.create(fundName).externalId(UUID.randomUUID().toString()).build();
        Integer outcome_fund_creation = FundsResourceHandler.createFund(new Gson().toJson(fh), requestSpec, responseSpec);
        Assertions.assertNotNull(outcome_fund_creation, "Could not create Fund");

        String paymentTypeName = PaymentTypeHelper.randomNameGenerator("P_T", 5);
        String paymentTypeDescription = PaymentTypeHelper.randomNameGenerator("PT_Desc", 15);

        PostPaymentTypesResponse paymentTypesResponse = paymentTypeHelper.createPaymentType(
                new PaymentTypeRequest().name(paymentTypeName).description(paymentTypeDescription).isCashPayment(true).position(1));
        Long outcome_payment_creation = paymentTypesResponse.getResourceId();

        Assertions.assertNotNull(outcome_payment_creation, "Could not create payment type");

        LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(requestSpec, responseSpec);
        Workbook workbook = loanTransactionHelper.getLoanWorkbook(DATE_FORMAT);

        // insert dummy data into loan Sheet
        Sheet loanSheet = workbook.getSheet(TemplatePopulateImportConstants.LOANS_SHEET_NAME);
        Row firstLoanRow = loanSheet.getRow(1);
        firstLoanRow.createCell(LoanConstants.OFFICE_NAME_COL).setCellValue(office.getName());
        firstLoanRow.createCell(LoanConstants.LOAN_TYPE_COL).setCellValue("Individual");
        firstLoanRow.createCell(LoanConstants.CLIENT_NAME_COL)
                .setCellValue(firstName + " " + lastName + "(" + outcome_client_creation + ")");
        firstLoanRow.createCell(LoanConstants.CLIENT_EXTERNAL_ID).setCellValue(externalId);
        firstLoanRow.createCell(LoanConstants.PRODUCT_COL).setCellValue(loanProductJson.getString("name"));
        firstLoanRow.createCell(LoanConstants.LOAN_OFFICER_NAME_COL).setCellValue((String) staffMap.get("displayName"));

        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.US);
        final LocalDate localDate = LocalDate.parse("17 May 2017", dateFormat);

        firstLoanRow.createCell(LoanConstants.SUBMITTED_ON_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.APPROVED_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.DISBURSED_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.DISBURSED_PAYMENT_TYPE_COL).setCellValue(paymentTypeName);
        firstLoanRow.createCell(LoanConstants.FUND_NAME_COL).setCellValue(fundName);
        firstLoanRow.createCell(LoanConstants.PRINCIPAL_COL).setCellValue(loanProductJson.getFloat("principal"));
        firstLoanRow.createCell(LoanConstants.NO_OF_REPAYMENTS_COL).setCellValue(loanProductJson.getInt("numberOfRepayments"));
        firstLoanRow.createCell(LoanConstants.REPAID_EVERY_COL).setCellValue(loanProductJson.getInt("repaymentEvery"));
        firstLoanRow.createCell(LoanConstants.REPAID_EVERY_FREQUENCY_COL)
                .setCellValue(loanProductJson.getString("repaymentFrequencyType.value"));
        firstLoanRow.createCell(LoanConstants.LOAN_TERM_COL)
                .setCellValue(loanProductJson.getInt("repaymentEvery") * loanProductJson.getInt("numberOfRepayments"));
        firstLoanRow.createCell(LoanConstants.LOAN_TERM_FREQUENCY_COL)
                .setCellValue(loanProductJson.getString("repaymentFrequencyType.value"));
        firstLoanRow.createCell(LoanConstants.NOMINAL_INTEREST_RATE_COL).setCellValue(loanProductJson.getDouble("interestRatePerPeriod"));
        firstLoanRow.createCell(LoanConstants.NOMINAL_INTEREST_RATE_FREQUENCY_COL)
                .setCellValue(loanProductJson.getString("interestRateFrequencyType.value"));
        firstLoanRow.createCell(LoanConstants.AMORTIZATION_COL).setCellValue(loanProductJson.getString("amortizationType.value"));
        firstLoanRow.createCell(LoanConstants.INTEREST_METHOD_COL).setCellValue(loanProductJson.getString("interestType.value"));
        firstLoanRow.createCell(LoanConstants.INTEREST_CALCULATION_PERIOD_COL)
                .setCellValue(loanProductJson.getString("interestCalculationPeriodType.value"));
        firstLoanRow.createCell(LoanConstants.ARREARS_TOLERANCE_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.REPAYMENT_STRATEGY_COL)
                .setCellValue(loanProductJson.getString("transactionProcessingStrategyName"));
        firstLoanRow.createCell(LoanConstants.GRACE_ON_PRINCIPAL_PAYMENT_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.GRACE_ON_INTEREST_PAYMENT_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.GRACE_ON_INTEREST_CHARGED_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.FIRST_REPAYMENT_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.TOTAL_AMOUNT_REPAID_COL).setCellValue(6000);
        firstLoanRow.createCell(LoanConstants.LAST_REPAYMENT_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.REPAYMENT_TYPE_COL).setCellValue(paymentTypeName);
        firstLoanRow.createCell(LoanConstants.LOAN_COLLATERAL_ID).setCellValue(collaterals.get(0).get("clientCollateralId").toString());
        firstLoanRow.createCell(LoanConstants.LOAN_COLLATERAL_QUANTITY).setCellValue(collaterals.get(0).get("quantity").toString());
        firstLoanRow.createCell(LoanConstants.CHARGE_NAME_1).setCellValue(disbursementChargeJSON.getString("name"));
        firstLoanRow.createCell(LoanConstants.CHARGE_AMOUNT_1).setCellValue(disbursementChargeJSON.getFloat("amount"));
        firstLoanRow.createCell(LoanConstants.CHARGE_AMOUNT_TYPE_1).setCellValue(disbursementChargeJSON.getString("chargeCalculationType"));

        String currentdirectory = new File("").getAbsolutePath();
        File directory = new File(currentdirectory + File.separator + "src" + File.separator + "integrationTest" + File.separator
                + "resources" + File.separator + "bulkimport" + File.separator + "importhandler" + File.separator + "loan");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File file = new File(directory + File.separator + "Loan.xls");
        OutputStream outputStream = new FileOutputStream(file);
        workbook.write(outputStream);
        outputStream.close();

        String importDocumentId = loanTransactionHelper.importLoanTemplate(file);
        file.delete();
        Assertions.assertNotNull(importDocumentId);

        // Wait for the creation of output excel
        Thread.sleep(10000);

        // check status column of output excel
        String location = loanTransactionHelper.getOutputTemplateLocation(importDocumentId);
        FileInputStream fileInputStream = new FileInputStream(location);
        Workbook outputworkbook = new HSSFWorkbook(fileInputStream);
        Sheet outputLoanSheet = outputworkbook.getSheet(TemplatePopulateImportConstants.LOANS_SHEET_NAME);
        Row row = outputLoanSheet.getRow(1);

        LOG.info("Output location: {}", location);
        LOG.info("Failure reason column: {}", row.getCell(LoanConstants.FAILURE_REPORT_COL).getStringCellValue());

        Assertions.assertEquals("Imported", row.getCell(LoanConstants.STATUS_COL).getStringCellValue());
        outputworkbook.close();

        firstLoanRow.getCell(LoanConstants.CLIENT_NAME_COL).setCellValue(sameOfficeImportClient.displayName());
        firstLoanRow.getCell(LoanConstants.CLIENT_EXTERNAL_ID).setCellValue(sameOfficeImportClient.externalId);
        firstLoanRow.getCell(LoanConstants.LOAN_OFFICER_NAME_COL).setCellValue((String) scopedStaff.get("displayName"));
        firstLoanRow.getCell(LoanConstants.LOAN_COLLATERAL_ID).setCellValue(sameOfficeImportClient.clientCollateralId.toString());
        Assertions.assertEquals(TemplatePopulateImportConstants.STATUS_CELL_IMPORTED,
                importAndGetStatus(scopedLoanTransactionHelper, workbook));

        firstLoanRow.getCell(LoanConstants.CLIENT_NAME_COL).setCellValue(outOfScopeImportClient.displayName());
        firstLoanRow.getCell(LoanConstants.CLIENT_EXTERNAL_ID).setCellValue(outOfScopeImportClient.externalId);
        firstLoanRow.getCell(LoanConstants.LOAN_COLLATERAL_ID).setCellValue(outOfScopeImportClient.clientCollateralId.toString());
        Assertions.assertEquals(TemplatePopulateImportConstants.STATUS_CREATION_FAILED,
                importAndGetStatus(scopedLoanTransactionHelper, workbook));
    }

    private ImportedClient createImportedClient(final Integer officeId, final Integer collateralProductId) {
        final String firstName = Utils.randomStringGenerator("Import_Client_", 5);
        final String lastName = Utils.randomStringGenerator("Import_Last_", 4);
        final String externalId = UUID.randomUUID().toString();
        final HashMap<String, Object> clientMap = new HashMap<>();
        clientMap.put("officeId", officeId.toString());
        clientMap.put("firstname", firstName);
        clientMap.put("lastname", lastName);
        clientMap.put("externalId", externalId);
        clientMap.put("dateFormat", DATE_FORMAT);
        clientMap.put("legalFormId", 1);
        clientMap.put("locale", "en");
        clientMap.put("active", "true");
        clientMap.put("activationDate", "04 March 2011");
        final Integer clientId = Utils.performServerPost(requestSpec, responseSpec, CREATE_CLIENT_URL, new Gson().toJson(clientMap),
                "clientId");
        Assertions.assertNotNull(clientId, "Could not create import client");
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                String.valueOf(clientId), collateralProductId);
        Assertions.assertNotNull(clientCollateralId, "Could not create import client collateral");
        return new ImportedClient(clientId, firstName, lastName, externalId, clientCollateralId);
    }

    private String importAndGetStatus(final LoanTransactionHelper helper, final Workbook workbook)
            throws IOException, InterruptedException {
        final File directory = new File(
                new File("").getAbsolutePath() + File.separator + "src" + File.separator + "integrationTest" + File.separator + "resources"
                        + File.separator + "bulkimport" + File.separator + "importhandler" + File.separator + "loan");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        final File file = new File(directory, "Loan.xls");
        try (OutputStream outputStream = new FileOutputStream(file)) {
            workbook.write(outputStream);
        }
        final String importDocumentId = helper.importLoanTemplate(file);
        file.delete();
        Assertions.assertNotNull(importDocumentId);
        String location = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            Thread.sleep(5000);
            final String outputLocation = helper.getOutputTemplateLocation(importDocumentId);
            if (!outputLocation.equals(location)) {
                location = outputLocation;
                LOG.info("Import document {} output location: {}", importDocumentId, location);
            }
            if (location.contains(File.separator + "IMPORT" + File.separator)) {
                continue;
            }
            final File outputFile = new File(location);
            if (outputFile.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(outputFile);
                        Workbook outputWorkbook = new HSSFWorkbook(fileInputStream)) {
                    final Row outputRow = outputWorkbook.getSheet(TemplatePopulateImportConstants.LOANS_SHEET_NAME).getRow(1);
                    if (outputRow != null && outputRow.getCell(LoanConstants.STATUS_COL) != null) {
                        final String status = outputRow.getCell(LoanConstants.STATUS_COL).getStringCellValue();
                        if (!status.isBlank()) {
                            LOG.info("Import document {} completed with status {}", importDocumentId, status);
                            return status;
                        }
                    }
                }
            }
        }
        LOG.warn("Import document {} did not produce a status at {}", importDocumentId, location);
        return "<missing import status>";
    }

    private static final class ImportedClient {

        private final Integer id;
        private final String firstName;
        private final String lastName;
        private final String externalId;
        private final Integer clientCollateralId;

        private ImportedClient(final Integer id, final String firstName, final String lastName, final String externalId,
                final Integer clientCollateralId) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.externalId = externalId;
            this.clientCollateralId = clientCollateralId;
        }

        private String displayName() {
            return firstName + " " + lastName + "(" + id + ")";
        }
    }
}
