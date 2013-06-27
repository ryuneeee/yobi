package controllers;

import info.schleichardt.play2.mailplugin.Mailer;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import play.Logger;
import play.db.ebean.Model;

import models.resource.Resource;

import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import play.data.Form;
import play.libs.Akka;
import play.mvc.*;
import utils.AccessControl;

import utils.Callback;
import utils.Constants;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@link BoardApp}과 {@link IssueApp}에서 공통으로 사용하는 기능을 담고 있는 컨트롤러 클래스
 */
public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;

    /**
     * 검색 조건
     */
    public static class SearchCondition {
        public String orderBy;
        public String orderDir;
        public String filter;
        public int pageNum;

        /**
         * 기본 검색 조건으로 id 역순이며 1페이지를 보여준다.
         */
        public SearchCondition() {
            this.orderDir = Direction.DESC.direction();
            this.orderBy = "id";
            this.filter = "";
            this.pageNum = 1;
        }
    }

    protected static interface Notification {
        public String getTitle();
        public String getHtmlMessage();
        public String getPlainMessage();
        public Set<User> getReceivers();
        public String getMessage();
        public String getUrlToView();
    }

    protected static abstract class AbstractNotification implements Notification {

        public String getHtmlMessage() {
            return String.format(
                    "<pre>%s</pre><hr><a href=\"%s\">%s</a>",
                    getMessage(), getUrlToView(), "View it on HIVE");
        }

        public String getPlainMessage() {
            return String.format(
                    "%s\n\n--\nView it on %s",
                    getMessage(), getUrlToView());
        }

    }

    /**
     * 새 댓글 저장 핸들러
     *
     * {@code commentForm}에서 입력값을 꺼내 현재 사용자를 작성자로 설정하고 댓글을 저장한다.
     * 현재 사용자 임시 저장소에 있는 첨부파일을 댓글의 첨부파일로 옮긴다.
     *
     * @param comment
     * @param commentForm
     * @param toView
     * @param containerUpdater
     * @return
     * @throws IOException
     */
    public static Result newComment(final Comment comment, Form<? extends Comment> commentForm, final Call toView, Callback containerUpdater) throws IOException {
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(toView);
        }

        comment.setAuthor(UserApp.currentUser());
        containerUpdater.run(); // this updates comment.issue or comment.posting;
        comment.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), comment.asResource());

        final AbstractPosting post = comment.getParent();
        Notification noti = new AbstractNotification() {
            public String getTitle() {
                return String.format(
                        "Re: [%s] %s (#%d)",
                        post.project.name, post.title, post.getNumber());
            }

            public Set<User> getReceivers() {
                return post.getWatchers();
            }

            @Override
            public String getMessage() {
                return comment.contents;
            }

            @Override
            public String getUrlToView() {
                return toView.absoluteURL(request());
            }
        };

        sendNotification(noti);

        return redirect(toView);
    }

    /**
     * 어떤 게시물이 등록되었을 때, 그 프로젝틑 지켜보는 사용자들에게 알림 메일을 발송한다.
     *
     * @param noti
     * @see <a href="https://github.com/nforge/hive/blob/master/docs/technical/watch.md>watch.md</a>
     */
    protected static void sendNotification(Notification noti) {
        final HtmlEmail email = new HtmlEmail();

        try {
            User sender = UserApp.currentUser();
            email.setFrom(sender.email, sender.name);
            play.Configuration config = play.Configuration.root();
            email.addTo(config.getString("smtp.user") + "@" + config.getString("smtp.domain"));
            Set<User> receivers = noti.getReceivers();
            if(receivers.isEmpty()) {
                return;
            }
            for (User receiver : receivers) {
                email.addBcc(receiver.email, receiver.name);
            }
            email.setSubject(noti.getTitle());
            email.setHtmlMsg(noti.getHtmlMessage());
            email.setTextMsg(noti.getPlainMessage());
            email.setCharset("utf-8");
        } catch (Exception e) {
            Logger.warn("Failed to send a notification: "
                    + email + "\n" + ExceptionUtils.getStackTrace(e));
        }

        Callable<Object> sendingMail = new Callable<Object>() {
            private Set<User> watchers;

            public Object call() throws EmailException {
                try {
                    Mailer.send(email);
                } catch (Exception e) {
                    Logger.warn("Failed to send a notification: "
                            + email + "\n" + ExceptionUtils.getStackTrace(e));
                }
                return null;
            }
        };

        Akka.future(sendingMail);
    }

    /**
     * {@code target}을 삭제하고 {@code redirectTo}로 이동한다.
     *
     * when: 게시물이나 이슈 또는 그곳에 달린 댓글을 삭제할 때 사용한다.
     *
     * @param target
     * @param resource
     * @param redirectTo
     * @return
     */
    protected static Result delete(Model target, Resource resource, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), resource, Operation.DELETE)) {
            return forbidden();
        }

        target.delete();

        return redirect(redirectTo);
    }

    /**
     * {@code posting}에 {@code original} 정보를 채우고 갱신한다.
     *
     * when: 게시물이나 이슈를 수정할 떄 사용한다.
     *
     * 게시물이나 이슈가 수정될 때 {@code noti} 객체가 null이 아니면 알림을 발송한다.
     *
     * @param original
     * @param posting
     * @param postingForm
     * @param redirectTo
     * @param updatePosting
     * @param noti
     * @return
     */
    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Callback updatePosting, Notification noti) {
        if (postingForm.hasErrors()) {
            return badRequest(postingForm.errors().toString());
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), original.asResource(), Operation.UPDATE)) {
            return forbidden(views.html.error.forbidden.render(original.project));
        }

        posting.id = original.id;
        posting.createdDate = original.createdDate;
        posting.authorId = original.authorId;
        posting.authorLoginId = original.authorLoginId;
        posting.authorName = original.authorName;
        posting.project = original.project;
        updatePosting.run();
        posting.update();
        posting.updateProperties();

        // Attach the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), original.asResource());

        if(noti != null) {
            sendNotification(noti);
        }

        return redirect(redirectTo);
    }

    /**
     * 새 게시물 또는 이슈 생성 권한이 있는지 확인하고 {@code content}를 보여준다.
     *
     * when: 게시물이나 이슈 생성할 때 사용한다.
     *
     * @param project
     * @param resourceType
     * @param content
     * @return
     */
    public static Result newPostingForm(Project project, ResourceType resourceType, Content content) {
        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, resourceType)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        return ok(content);
    }

    /**
     * 현재 사용자가 게시물을 명시적으로 지켜보는 것으로 설정한다.
     *
     * @param target
     * @return
     */
    public static Result watch(AbstractPosting target) {
        User user = UserApp.currentUser();

        if (!AccessControl.isAllowed(user, target.asResource(), Operation.READ)) {
            return forbidden("You have no permission to do that.");
        }

        if (user.isAnonymous()) {
            return forbidden("Anonymous cannot watch it.");
        }

        target.watch(user);

        return ok();
    }

    /**
     * 현재 사용자가 게시물을 명시적으로 무시하는(지켜보지 않는) 것으로 설정한다.
     *
     * @param target
     * @return
     */
    public static Result unwatch(AbstractPosting target) {
        User user = UserApp.currentUser();

        if (user.isAnonymous()) {
            return forbidden("Anonymous cannot unwatch it.");
        }

        target.unwatch(user);

        return ok();
    }
}
