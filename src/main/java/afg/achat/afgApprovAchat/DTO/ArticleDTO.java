package afg.achat.afgApprovAchat.DTO;

import afg.achat.afgApprovAchat.model.Article;

public record ArticleDTO(
        String codeArticle,
        String designation,
        int seuilMin,
        FamilleDTO famille,
        UdmDTO udm
) {
    public static ArticleDTO from(Article a) {
        return new ArticleDTO(
                a.getCodeArticle(),
                a.getDesignation(),
                a.getSeuilMin(),
                a.getFamille() != null
                        ? new FamilleDTO(a.getFamille().getId(), a.getFamille().getDescription())
                        : null,
                a.getUdm() != null
                        ? new UdmDTO(a.getUdm().getId(), a.getUdm().getDescription(), a.getUdm().getAcronyme())
                        : null
        );
    }

    public record FamilleDTO(int id, String description) {}
    public record UdmDTO(int id, String description, String acronyme) {}
}