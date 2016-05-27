/**
 * 
 */
package br.com.sambarest.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import br.com.sambarest.model.Arquivo;
import br.com.sambarest.util.ParametersUtil;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * @author Leandro
 *
 */

@Path("/arquivo")
public class DefaultArquivoService {
	
	static final String PARAM_BUCKET_NAME = "bucket";
	static final String PARAM_BUCKET_URL = "urlbucket";
	static final String PARAM_ACCESS_KEY = "accesskey";
	static final String PARAM_SECRET_KEY = "secretkey";
	static final String PARAM_ZENCODER_URL = "zencoderurl";
	static final String PARAM_ZENCODER_KEY = "zencoderkey";
	static final String PARAM_ZENCODER_GRANTEE = "zencodergrantee";
	static final String READ_PERMISSION = "READ";
	static final Integer ONE_MB = 1000000;

	@GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public List<Arquivo> listAll() {
		List<Arquivo> arquivos = new ArrayList<Arquivo>();
		AmazonS3 s3client = buildS3Client();

		try {
			final ListObjectsV2Request req = new ListObjectsV2Request()
					.withBucketName(ParametersUtil.recupera(PARAM_BUCKET_NAME))
					.withMaxKeys(2);
			ListObjectsV2Result result;
			do {
				result = s3client.listObjectsV2(req);

				for (S3ObjectSummary objectSummary : result
						.getObjectSummaries()) {
					arquivos.add(buildNewArquivo(objectSummary));
				}
				req.setContinuationToken(result.getNextContinuationToken());
			} while (result.isTruncated() == true);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return arquivos;
	}	
	
	/**
	 * Obtem diretorio temporario do ambiente.
	 * 
	 */
	public String getTempDir() {
		return System.getProperty("java.io.tmpdir") + File.separator;
	}

	/**
	 * Cria o client S3 com as credenciais necessarias.
	 * 
	 */
	private AmazonS3Client buildS3Client() {
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(
				ParametersUtil.recupera(PARAM_ACCESS_KEY),
				ParametersUtil.recupera(PARAM_SECRET_KEY));
		return new AmazonS3Client(awsCreds);
	}

	/**
	 * Converte o objeto S3 em um objeto Arquivo.
	 * 
	 */
	private Arquivo buildNewArquivo(S3ObjectSummary objectSummary) {
		Arquivo arquivo = new Arquivo();
		arquivo.setKey(objectSummary.getKey());

		long size = objectSummary.getSize();
		if (size > 0) {
			arquivo.setSize((double) (size / ONE_MB));
		} else {
			arquivo.setSize((double) size);
		}

		return arquivo;
	}

}
