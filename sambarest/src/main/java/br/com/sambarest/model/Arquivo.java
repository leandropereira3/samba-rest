/**
 * 
 */
package br.com.sambarest.model;

import javax.xml.bind.annotation.XmlRootElement;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Leandro
 *
 */

@XmlRootElement
@EqualsAndHashCode(of="key")
@Data
public class Arquivo {

	private String key;
	private Double size;
	private String url;
}
