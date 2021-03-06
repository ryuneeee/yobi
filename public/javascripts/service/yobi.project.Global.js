/**
 * @(#)yobi.project.Global.js 2013.06.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */
/*
 * 프로젝트 페이지 전역에 영향을 주는 공통모듈
 * prjmenu.scala.html 에서 호출함
 */
(function(ns) {
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions) {
        var htVar = {};
        var htElement = {};

        /**
         * 모듈 초기화
         * initialize
         */
        function _init(htOptions) {
            _initVar(htOptions);
            _initElement();
            _attachEvent();

            _initShortcutKey(htOptions.htKeyMap);
        }

        /**
         * 변수 초기화
         * initialize normal variables
         */
        function _initVar(htOptions){
            htVar.sRepoURL = htOptions.sRepoURL;
        }

        /**
         * 엘리먼트 변수 초기화
         * initialize element variables
         */
        function _initElement() {
            htElement.welProjectMenu = $(".project-menu");
            htElement.welProjectMenuWrap = $(".project-menu-wrap");

            htElement.welBtnWatch   = $(".watchBtn, #btnWatch");
            htElement.welBtnEnroll  = $(".enrollBtn");
            htElement.welBtnClone   = $("#btnClone");
            htElement.welForkedFrom = $("#forkedFrom");

            // 프로젝트 페이지에서만.
            // project.Global 은 프로필 페이지에서도 공유합니다
            if(htElement.welBtnClone.length > 0){
                htElement.welShowRepoURL = $("#showRepoURL");
                _placeShowRepoURL();
            }
        }

        /**
         * 저장소 URL 정보 표시 레이어 위치 조절
         */
        function _placeShowRepoURL(){
            var nWidth   = htElement.welShowRepoURL.width();
            var nBtnHeight = htElement.welBtnClone.height();
            var htOffset   = htElement.welBtnClone.offset();
            
            htElement.welShowRepoURL.css({
                "left": (htOffset.left - (nWidth / 2)) + "px",
                "top" : (nBtnHeight + 15) + "px"
            });
        }

        /**
         * 이벤트 핸들러 초기화
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welBtnWatch.click(_onClickBtnWatch);
            htElement.welBtnEnroll.click(_onClickBtnEnroll);

            // 내용은 data-content 속성으로 scala 파일 내에 있음.
            htElement.welForkedFrom.popover({
                "html"   : true
            });

            // 저장소 URL 표시 관련
            if(htElement.welBtnClone.length > 0){
                htElement.welBtnClone.click(_onClickBtnClone);
                htElement.welShowRepoURL.find(".repo-url").click(function(){
                    $(this).select();
                });
                $(window).on("resize", _placeShowRepoURL);
            }
        }

        /**
         * Watch 버튼 클릭시 이벤트 핸들러
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var sURL = $(this).attr('href');
            //$('<form action="' + sURL + '" method="post"></form>').submit();
            $.ajax(sURL, {
                "method" : "post",
                "success": function(){
                    document.location.reload();
                },
                "error": function(){
                    $yobi.notify("Server Error");
                }
            })

            weEvt.preventDefault();
            return false;
        }

        /**
         * Enroll 버튼 클릭시 이벤트 핸들러
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnEnroll(weEvt){
            $('<form action="' + $(this).attr('href') + '" method="post"></form>').submit();
            weEvt.preventDefault();
            return false;
        }

        /**
         * 프로젝트 전역 공통 단축키
         * @param {Hash Table} htKeyMap
         * @require yobi.ShortcutKey
         */
        function _initShortcutKey(htKeyMap){
            yobi.ShortcutKey.setKeymapLink(htKeyMap);
        }

        /**
         * Clone 버튼 클릭시 이벤트 핸들러
         */
        function _onClickBtnClone(){
            htElement.welShowRepoURL.toggle();
            
            if (!htVar.bInitClipboard) {
                // 주소 복사 버튼
                htElement.welShowRepoURL.find(".copy-url").zclip({
                    "path": "/assets/javascripts/lib/jquery/ZeroClipboard.swf",
                    "copy": htVar.sRepoURL
                });

                htVar.bInitClipboard = true;                
            }
        }

        _init(htOptions || {});
    };
})("yobi.project.Global");