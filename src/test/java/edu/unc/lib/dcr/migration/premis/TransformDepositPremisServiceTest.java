/**
 * Copyright 2008 The University of North Carolina at Chapel Hill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unc.lib.dcr.migration.premis;

import static edu.unc.lib.dcr.migration.premis.Premis2Constants.INITIATOR_ROLE;
import static edu.unc.lib.dcr.migration.premis.Premis2Constants.METS_NORMAL_AGENT;
import static edu.unc.lib.dcr.migration.premis.Premis2Constants.VALIDATION_TYPE;
import static edu.unc.lib.dcr.migration.premis.Premis2Constants.VIRUS_AGENT;
import static edu.unc.lib.dcr.migration.premis.Premis2Constants.VIRUS_CHECK_TYPE;
import static edu.unc.lib.dcr.migration.premis.PremisTransformationService.getTransformedPremisPath;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.EVENT_DATE;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.addAgent;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.addEvent;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.buildPremisListFile;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.createPremisDoc;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.listEventResources;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.readTransformedLog;
import static edu.unc.lib.dcr.migration.premis.TestPremisEventHelpers.serializeXMLFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.unc.lib.dl.event.PremisLoggerFactory;
import edu.unc.lib.dl.fcrepo4.RepositoryPIDMinter;
import edu.unc.lib.dl.fedora.PID;
import edu.unc.lib.dl.rdf.Premis;

/**
 * @author bbpennel
 */
public class TransformDepositPremisServiceTest {

    @Rule
    public final TemporaryFolder tmpFolder = new TemporaryFolder();

    private Path outputPath;
    private Path originalLogsPath;
    private Path premisListPath;

    private RepositoryPIDMinter pidMinter;
    private PremisLoggerFactory premisLoggerFactory;

    private TransformDepositPremisService service;

    @Before
    public void setup() throws Exception {
        pidMinter = new RepositoryPIDMinter();
        premisLoggerFactory = new PremisLoggerFactory();

        outputPath = tmpFolder.newFolder("output").toPath();
        originalLogsPath = tmpFolder.newFolder("originals").toPath();
        premisListPath = tmpFolder.newFile("premis_list.txt").toPath();

        service = new TransformDepositPremisService(premisListPath, outputPath);
        service.setPidMinter(pidMinter);
        service.setPremisLoggerFactory(premisLoggerFactory);
    }

    @Test
    public void transformMultipleObjectEvents() throws Exception {
        PID pid1 = pidMinter.mintDepositRecordPid();
        createLogWithVirusEvent(pid1);

        PID pid2 = pidMinter.mintDepositRecordPid();
        createLogWithMetsEvent(pid2);

        buildPremisListFile(originalLogsPath, premisListPath);

        int result = service.perform();
        assertEquals(0, result);

        Model obj1Model = readTransformedLog(outputPath, pid1);
        List<Resource> obj1Events = listEventResources(pid1, obj1Model);
        assertEquals(1, obj1Events.size());
        assertTrue(obj1Events.get(0).hasProperty(RDF.type, Premis.VirusCheck));

        Model obj2Model = readTransformedLog(outputPath, pid2);
        List<Resource> obj2Events = listEventResources(pid2, obj2Model);
        assertTrue(obj2Events.get(0).hasProperty(RDF.type, Premis.Validation));
    }

    @Test
    public void transformOneFailure() throws Exception {
        PID pid1 = pidMinter.mintDepositRecordPid();
        createLogWithVirusEvent(pid1);

        PID pid2 = pidMinter.mintDepositRecordPid();
        Path xmlPath = originalLogsPath.resolve(pid2.getId() + ".xml");
        FileUtils.write(xmlPath.toFile(), "Super bad", UTF_8);

        PID pid3 = pidMinter.mintDepositRecordPid();
        createLogWithMetsEvent(pid3);

        buildPremisListFile(originalLogsPath, premisListPath);

        int result = service.perform();
        assertEquals(1, result);

        Model obj1Model = readTransformedLog(outputPath, pid1);
        List<Resource> obj1Events = listEventResources(pid1, obj1Model);
        assertEquals(1, obj1Events.size());
        assertTrue(obj1Events.get(0).hasProperty(RDF.type, Premis.VirusCheck));

        Model obj3Model = readTransformedLog(outputPath, pid3);
        List<Resource> obj3Events = listEventResources(pid3, obj3Model);
        assertTrue(obj3Events.get(0).hasProperty(RDF.type, Premis.Validation));

        Path failedPath = getTransformedPremisPath(outputPath, pid2, true);
        assertFalse("Transformed log for invalid log should not exist",
                Files.exists(failedPath));
    }

    @Test
    public void transformManyObjects() throws Exception {
        for (int i = 0; i < 500; i++) {
            PID pid = pidMinter.mintDepositRecordPid();
            createLogWithVirusEvent(pid);
        }
        buildPremisListFile(originalLogsPath, premisListPath);

        int result = service.perform();
        assertEquals(0, result);

        long numTransformed = Files.walk(outputPath)
                .filter(Files::isRegularFile)
                .count();

        assertEquals(500, numTransformed);
    }

    private void createLogWithVirusEvent(PID pid) throws Exception {
        Document premisDoc = createPremisDoc(pid);
        String detail = "28 files scanned for viruses.";
        Element eventEl1 = addEvent(premisDoc, VIRUS_CHECK_TYPE, detail, EVENT_DATE);
        addInitiatorAgent(eventEl1, VIRUS_AGENT);

        serializeXMLFile(originalLogsPath, pid, premisDoc);
    }

    private void createLogWithMetsEvent(PID pid) throws Exception {
        Document premisDoc = createPremisDoc(pid);
        String detail = "METS schema(s) validated";
        Element eventEl = addEvent(premisDoc, VALIDATION_TYPE, detail, EVENT_DATE);
        addInitiatorAgent(eventEl, METS_NORMAL_AGENT);

        serializeXMLFile(originalLogsPath, pid, premisDoc);
    }

    private void addInitiatorAgent(Element eventEl, String agent) {
        addAgent(eventEl, "PID", INITIATOR_ROLE, agent);
    }
}