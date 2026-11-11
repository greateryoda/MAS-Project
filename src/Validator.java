// Montre une proposition a l'utilisateur et renvoie sa decision.
// Console pour l'instant, une interface graphique pourra implementer la meme interface.
public interface Validator {
    Feedback review(Proposal proposal);

    // tous les brouillons ont ete refuses : l'utilisateur ecrit la reponse lui-meme
    // renvoie null s'il ne veut pas repondre
    String writeReply(Email email);
}
