package afg.achat.afgApprovAchat.email;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public class Mail {

	private String templateName;
	private String from_adress;
	private String from_name;
    private String mailTo;
    private String subject;
    private List<MultipartFile> attachments;
    private Map<String, Object> props;
    private String[] cc;
    
	public Mail(String templateName, String mailTo, String subject, Map<String, Object> props) {
		super();
		this.templateName = templateName;
		this.mailTo = mailTo;
		this.subject = subject;
		this.props = props;
	}
	
	public Mail(String templateName, String mailTo, String subject, Map<String, Object> props, String[] cc) {
		super();
		this.templateName = templateName;
		this.mailTo = mailTo;
		this.subject = subject;
		this.props = props;
		this.cc = cc;
	}

	public Mail() {
		super();
	}
	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}
	public String getFrom_adress() {
		return from_adress;
	}
	public void setFrom_adress(String from_adress) {
		this.from_adress = from_adress;
	}
	public String getFrom_name() {
		return from_name;
	}

	public void setFrom_name(String from_name) {
		this.from_name = from_name;
	}

	public String getMailTo() {
		return mailTo;
	}
	public void setMailTo(String mailTo) {
		this.mailTo = mailTo;
	}
	public String getSubject() {
		return subject;
	}
	public void setSubject(String subject) {
		this.subject = subject;
	}

	public Map<String, Object> getProps() {
		return props;
	}
	public void setProps(Map<String, Object> props) {
		this.props = props;
	}
	public String[] getCc() {
		return cc;
	}
	public void setCc(String[] cc) {
		this.cc = cc;
	}
	
	public List<MultipartFile> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<MultipartFile> attachments) {
		this.attachments = attachments;
	}

	@Override
	public String toString() {
		return "Mail [templateName=" + templateName + ", from_adress=" + from_adress + ", from_name=" + from_name
				+ ", mailTo=" + mailTo + ", subject=" + subject + ", attachments=" + attachments + ", props=" + props
				+ ", cc=" + cc + "]";
	}
	
}
