package afg.achat.afgApprovAchat.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

//******************************************************************************
//**  ActiveDirectory
//*****************************************************************************/

/**
 *   Provides static methods to authenticate users, change passwords, etc. 
 *
 ******************************************************************************/
@Service
public class ActiveDirectory {
 
    private static String[] userAttributes = {
        "distinguishedName","cn","name","uid",
        "sn","givenname","memberOf","samaccountname",
        "userPrincipalName","mail","password"
    };
 
    private final LdapConfProperties ldapConfProperties ; 
    
    @Autowired
    public ActiveDirectory(LdapConfProperties ldapConfProperties){
    	this.ldapConfProperties = ldapConfProperties ;
    }
 
    public boolean authentify(String userName, String password) throws NamingException {
        boolean isAuthenticated = false;
        LdapContext ctx = null;

        try {
            // Configuration pour l'utilisateur technique (comme dans getUser)
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, "ldap://10.25.10.10");
            env.put(Context.SECURITY_PRINCIPAL, "CN=glpi mada,OU=Service,OU=User Accounts,OU=Madagascar,DC=afgbank,DC=com");
            env.put(Context.SECURITY_CREDENTIALS, "Services!2024");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");

            // Connexion avec l'utilisateur technique
            ctx = new InitialLdapContext(env, null);

            // Recherche du DN de l'utilisateur à authentifier
            String baseDN = "DC=afgbank,DC=com";
            String filter = "(userPrincipalName=" + userName + ")";
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<SearchResult> results = ctx.search(baseDN, filter, controls);

            if (results.hasMore()) {
                SearchResult result = results.next();
                String userDN = result.getNameInNamespace();

                // Tentative de connexion avec le DN de l'utilisateur et son mot de passe
                Hashtable<String, String> userEnv = new Hashtable<>();
                userEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                userEnv.put(Context.PROVIDER_URL, "ldap://10.25.10.10");
                userEnv.put(Context.SECURITY_PRINCIPAL, userDN);
                userEnv.put(Context.SECURITY_CREDENTIALS, password);
                userEnv.put(Context.SECURITY_AUTHENTICATION, "simple");

                // Authentification directe avec le DN trouvé
                LdapContext userCtx = new InitialLdapContext(userEnv, null);
                userCtx.close();
                isAuthenticated = true;
            } else {
                System.out.println("Utilisateur LDAP non trouvé : " + userName);
            }

        } catch (CommunicationException e) {
            System.err.println("Erreur de communication avec le serveur LDAP");
            e.printStackTrace();
        } catch (NamingException e) {
            System.err.println("Échec d'authentification pour l'utilisateur : " + userName);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        return isAuthenticated;
    }



    public User getUser(String username) throws NamingException {
        LdapContext context = null;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, "ldap://10.25.10.10");
            env.put(Context.SECURITY_PRINCIPAL, "CN=glpi mada,OU=Service,OU=User Accounts,OU=Madagascar,DC=afgbank,DC=com");
            env.put(Context.SECURITY_CREDENTIALS, "Services!2024");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");

            context = new InitialLdapContext(env, null);
            System.out.println("Connexion LDAP établie.");
            String baseDN = "DC=afgbank,DC=com";
            String filter = "(userPrincipalName=" + username + ")";
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<SearchResult> results = context.search(baseDN, filter, controls);

            if (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();
                User user = new User();

                if (attrs.get("userPrincipalName") != null) {
                    user.setUserPrincipal(attrs.get("userPrincipalName").get().toString());
                }
                if (attrs.get("cn") != null) {
                    user.setCommonName(attrs.get("cn").get().toString());
                }
                if (attrs.get("distinguishedName") != null) {
                    user.setDistinguishedName(attrs.get("distinguishedName").get().toString());
                }
                if (attrs.get("mail") != null) {
                    user.setMail(attrs.get("mail").get().toString());
                }
                if (attrs.get("givenName") != null) {
                    user.setFirstName(attrs.get("givenName").get().toString());
                }

                return user;
            } else {
                System.out.println("Utilisateur non trouvé dans l'annuaire.");
                return null;
            }
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }
 
    public static User[] getUsers() throws NamingException {
        List<User> users = new ArrayList<>();

        String ldapURL = "ldap://10.25.10.10";
        String bindUserDN = "CN=glpi mada,OU=Service,OU=User Accounts,OU=Madagascar,DC=afgbank,DC=com";
        String bindPassword = "Services!2024";

        String searchBase = "OU=Enabled Users,OU=User Accounts,OU=Madagascar,dc=afgbank,dc=com";
        String filter = "(objectClass=user)";

        Hashtable<String, String> props = new Hashtable<>();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, ldapURL);
        props.put(Context.SECURITY_PRINCIPAL, bindUserDN);
        props.put(Context.SECURITY_CREDENTIALS, bindPassword);
        props.put(Context.SECURITY_AUTHENTICATION, "simple");

        LdapContext context = null;
        try {
            context = new InitialLdapContext(props, null);

            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(userAttributes);

            NamingEnumeration<SearchResult> results = context.search(searchBase, filter, controls);

            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();

                if (attrs.get("userPrincipalName") != null) {
                    try {
                        users.add(new User(attrs));
                    } catch (Exception ex) {
                        System.err.println("Erreur utilisateur LDAP : " + ex.getMessage());
                    }
                }
            }

        } catch (NamingException e) {
            System.err.println("Erreur de connexion ou de recherche LDAP : " + e.getMessage());
            throw e;
        } finally {
            if (context != null) {
                context.close();
            }
        }

        return users.toArray(new User[0]);
    }
 
 
    private static String toDC(String domainName) {
        StringBuilder buf = new StringBuilder();
        for (String token : domainName.split("\\.")) {
            if(token.length()==0)   continue;   // defensive check
            if(buf.length()>0)  buf.append(",");
            buf.append("DC=").append(token);
        }
        return buf.toString();
    }
 
 
  //**************************************************************************
  //** User Class
  //*************************************************************************/
  /** Used to represent a User in Active Directory
   */
    public static class User {
        
    	 private String distinguishedName;
    	    private String userPrincipal;
    	    private String commonName;
    	    private String mail;
    	    private String surname;
    	    private String firstName;
    	    private String samAccountName;
    	    private String username;

    	    public User() {
				super();
			}

			public User(Attributes attr) throws NamingException {
    	        this.distinguishedName = getAttribute(attr, "distinguishedName");
    	        this.userPrincipal = getAttribute(attr, "userPrincipalName");
    	        this.commonName = getAttribute(attr, "cn");
    	        this.mail = getAttribute(attr, "mail");
    	        this.surname = getAttribute(attr, "sn");
    	        this.firstName = getAttribute(attr, "givenName");
    	        this.samAccountName = getAttribute(attr, "samaccountname");
    	        this.username = getAttribute(attr, "uid");
    	    }

    	    // Getter pour les attributs
    	    public String getDistinguishedName() {
    	        return distinguishedName;
    	    }

    	    public String getUserPrincipal() {
    	        return userPrincipal;
    	    }

    	    public String getCommonName() {
    	        return commonName;
    	    }

    	    public String getMail() {
    	        return mail;
    	    }

    	    public String getSurname() {
    	        return surname;
    	    }

    	    public String getFirstName() {
    	        return firstName;
    	    }

    	    public String getSamAccountName() {
    	        return samAccountName;
    	    }

    	    public String getUsername() {
    	        return username;
    	    }

    	    public void setDistinguishedName(String distinguishedName) {
				this.distinguishedName = distinguishedName;
			}

			public void setUserPrincipal(String userPrincipal) {
				this.userPrincipal = userPrincipal;
			}

			public void setCommonName(String commonName) {
				this.commonName = commonName;
			}

			public void setMail(String mail) {
				this.mail = mail;
			}

			public void setSurname(String surname) {
				this.surname = surname;
			}

			public void setFirstName(String firstName) {
				this.firstName = firstName;
			}

			public void setSamAccountName(String samAccountName) {
				this.samAccountName = samAccountName;
			}

			public void setUsername(String username) {
				this.username = username;
			}

    	    private String getAttribute(Attributes attr, String attributeName) {
    	        try {
    	            Attribute attribute = attr.get(attributeName);
    	            if (attribute != null) {
    	                return (String) attribute.get();
    	            }
    	        } catch (NamingException e) {
    	            System.out.println("Erreur lors de la récupération de l'attribut : " + attributeName);
    	        }
    	        return null; 
    	    }

    	    @Override
    	    public String toString() {
    	        return "User{" +
    	                "distinguishedName='" + distinguishedName + '\'' +
    	                ", userPrincipal='" + userPrincipal + '\'' +
    	                ", commonName='" + commonName + '\'' +
    	                ", mail='" + mail + '\'' +
    	                ", surname='" + surname + '\'' +
    	                ", firstName='" + firstName + '\'' +
    	                ", samAccountName='" + samAccountName + '\'' +
    	                ", username='" + username + '\'' +
    	                '}';
    	    }
    }
}
