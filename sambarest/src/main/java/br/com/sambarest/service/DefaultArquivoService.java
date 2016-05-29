/**
 * 
 */
package br.com.sambarest.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.glassfish.jersey.media.multipart.FormDataParam;

import br.com.sambarest.model.Arquivo;
import br.com.sambarest.util.ParametersUtil;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
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
	
	@POST
	@Path("/upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response upload(@FormDataParam("file") InputStream uploadedInputStream,  
            @FormDataParam("file") org.glassfish.jersey.media.multipart.FormDataContentDisposition fileDetail){
		
		Response response = null;
		System.out.println(uploadedInputStream);		
		 
		System.out.println("file" + fileDetail.getFileName());
		File newFile = new File(getTempDir() + fileDetail.getFileName());

		// Cria um arquivo no diretorio temp com os dados do arquivo enviado
		InputStream inputStream = uploadedInputStream;
		try{
			OutputStream outputStream = new FileOutputStream(newFile);
			byte[] buffer = new byte[10 * 1024];
			for (int length; (length = inputStream.read(buffer)) != -1;) {
				outputStream.write(buffer, 0, length);
				outputStream.flush();
			}

			insertFile(newFile);			
			response = Response.status(200).entity("Arquivo enviado com sucesso!").build(); 
		}
		catch(Exception e){
			e.printStackTrace();
			response = Response.status(200).entity("Falha ao enviar arquivo!").build();
		}		
		
		return response;
	}	

	@Path("/get")
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
	 * Insere o arquivo no S3
	 */
	public PutObjectResult insertFile(File file) {
		AmazonS3 s3Client = buildS3Client();

		PutObjectRequest objRequest = new PutObjectRequest(
				ParametersUtil.recupera(PARAM_BUCKET_NAME), file.getName(),
				file);
		objRequest.setCannedAcl(CannedAccessControlList.PublicRead);
		PutObjectResult result = s3Client.putObject(objRequest);
		return result;
	}
	
	public void excluir(Arquivo selectedArquivo) {
		AmazonS3 s3Client = buildS3Client();
		s3Client.deleteObject(new DeleteObjectRequest(ParametersUtil
				.recupera(PARAM_BUCKET_NAME), selectedArquivo.getKey()));
	}
	
	@Path("/urlvideo/{param}")
	@GET
	@Produces({ MediaType.TEXT_PLAIN })
	@Consumes(MediaType.APPLICATION_JSON)
	public String getUrlFile(@PathParam("param") String param) {
		String key = param;		
		Response retorno = null;
		String ret = null;
		try {						
			@SuppressWarnings({ "deprecation", "resource" })
			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost(
					ParametersUtil.recupera(PARAM_ZENCODER_URL));
			// add header
			post.setHeader("Content-Type", "application/json");
			post.setHeader("Zencoder-Api-Key",
					ParametersUtil.recupera(PARAM_ZENCODER_KEY));

			JSONObject jsonObject = new JSONObject();
			JSONArray outputs = new JSONArray();
			JSONObject jsonAccessControl = new JSONObject();
			jsonAccessControl.put("permission", READ_PERMISSION);
			jsonAccessControl.put("grantee",
					ParametersUtil.recupera(PARAM_ZENCODER_GRANTEE));
			outputs.put(jsonAccessControl);
			jsonObject.put("test", "true");
			jsonObject.put("input", ParametersUtil.recupera(PARAM_BUCKET_URL)
					+ key);
			jsonObject.put("outputs", outputs);

			StringEntity se = new StringEntity(jsonObject.toString());
			post.setEntity(se);

			HttpResponse response = client.execute(post);

			BufferedReader rd = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent()));

			StringBuffer result = new StringBuffer();
			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}

			JSONObject obj = new JSONObject(result.toString());
			JSONArray outputsResponse = new JSONArray(obj.get("outputs")
					.toString());
			if (outputsResponse.length() > 0) {
				JSONObject resultado = outputsResponse.getJSONObject(0);
				ret = resultado.getString("url");
				retorno = Response.status(200).entity(resultado.getString("url")).build();		
			}

		} catch (Exception e) {
			e.printStackTrace();
			retorno = Response.status(200).entity("Falha ao obter endereço do vídeo.").build();
		}
		return ret;
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
