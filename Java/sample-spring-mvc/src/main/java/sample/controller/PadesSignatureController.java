package sample.controller;

import com.lacunasoftware.restpki.*;
import org.springframework.web.bind.annotation.*;
import sample.models.*;
import sample.util.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * This controller contains the server-side logic for the PAdES signature example. The client-side is implemented at:
 * - View: src/main/resources/templates/pades-signature.html
 * - JS: src/main/resources/static/js/app/pades-signature.js
 *
 * This controller uses the classes PadesSignatureStarter and PadesSignatureFinisher of the REST PKI Java client lib.
 * For more information, see:
 * https://restpki.lacunasoftware.com/Content/docs/java-client/index.html?com/lacunasoftware/restpki/PadesSignatureStarter.html
 * https://restpki.lacunasoftware.com/Content/docs/java-client/index.html?com/lacunasoftware/restpki/PadesSignatureFinisher.html
 */
@RestController
public class PadesSignatureController {

    /**
     * GET api/pades-signature
     *
     * This action is called by the page to initiate the signature process.
     */
    @RequestMapping(value = "/api/pades-signature", method = {RequestMethod.GET})
    public String get() throws IOException, RestException {

        // Instantiate the PadesSignatureStarter class, responsible for receiving the signature elements and start the
        // signature process
        PadesSignatureStarter signatureStarter = new PadesSignatureStarter(Util.getRestPkiClient());

        // Set the PDF to be signed, which in the case of this example is a fixed sample document
        signatureStarter.setPdfToSign(Util.getSampleDocContent());

        // Set the signature policy
        signatureStarter.setSignaturePolicy(SignaturePolicy.PadesBasic);

        // Set a SecurityContext to be used to determine trust in the certificate chain
        signatureStarter.setSecurityContext(Util.getSecurityContext());
        // Note: By changing the SecurityContext above you can accept only certificates from a certain PKI,
        // for instance, ICP-Brasil (SecurityContext.pkiBrazil).

        // Create a visual representation for the signature
        PadesVisualRepresentation visualRepresentation = new PadesVisualRepresentation();

        // Set the text that will be inserted in the signature visual representation with the date time of the signature.
        // The tags {{signerName}} and {{signerNationalId}} will be replaced by its real values present in the signer certificate
        visualRepresentation.setText(new PadesVisualText("Assinado por {{signerName}} (CPF {{signerNationalId}})", true));

        // Set a image to stamp the signature visual representation
        visualRepresentation.setImage(new PadesVisualImage(Util.getPdfStampContent(), "image/png"));

        // Set the position that the visual representation will be inserted in the document. The getFootnote() is an
        // auto positioning preset. It will insert the signature, and future signatures, ordered as a footnote of the
        // document
        visualRepresentation.setPosition(PadesVisualPositioning.getFootnote(Util.getRestPkiClient()));

        // Set the visual representation created
        signatureStarter.setVisualRepresentation(visualRepresentation);

        // Call the startWithWebPki, which initiates the signature. This yields the token,
        // a 43-character case-sensitive URL-safe string, which we'll send to the page in order to pass on the
        // signWithRestPki method of the Web PKI component.
        String token = signatureStarter.startWithWebPki();

        // Return the token to the page
        return token;
    }

    /**
     * POST api/pades-signature?token=xxx
     *
     * This action is called once the signature is complete on the client-side. The page sends back on the URL the token
     * originally yielded by the get() method.
     */
    @RequestMapping(value = "/api/pades-signature", method = {RequestMethod.POST})
    public SignatureCompleteResponse post(HttpServletRequest httpRequest, @RequestParam(value="token", required=true) String token) throws IOException, RestException {

        // Instantiate the PadesSignatureFinisher class, responsible for completing the signature process
        PadesSignatureFinisher signatureFinisher = new PadesSignatureFinisher(Util.getRestPkiClient());

        // Set the token previously yielded by the startWithWebPki() method (which we sent to the page and the page
        // sent us back on the URL)
        signatureFinisher.setToken(token);

        SignatureCompleteResponse response = new SignatureCompleteResponse();
        byte[] signedPdf;

        try {

            // Call the finish() method, which finalizes the signature process. Unlike the complete() method of the
            // Authentication class, this method throws an exception if validation of the signature fails.
            signedPdf = signatureFinisher.finish();

        } catch (ValidationException e) {

            // If validation of the signature failed, inform the page
            response.setSuccess(false);
            response.setMessage("A validation error has occurred");
            response.setValidationResults(e.getValidationResults().toString());
            return response;

        }

        // At this point, you'd typically store the signed PDF on your database. For demonstration purposes, we'll
        // store the PDF on a temporary folder and return to the page an ID that can be used to open the signed PDF.
        DatabaseMock dbMock = new DatabaseMock(httpRequest.getSession());
        String signatureId = dbMock.putSignedPdf(signedPdf);

        response.setSuccess(true);
        response.setSignatureId(signatureId);

        return response;
    }

}