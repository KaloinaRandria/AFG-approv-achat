(function ($) {
    var fileUploadCount = 0;
 
    $.fn.fileUpload = function () {
        return this.each(function () {
            var inputFile = $(this);
            var fileUploadId = `fileUpload-${++fileUploadCount}`;
            inputFile.attr({
                id: fileUploadId,
                /*accept: ".pdf,.doc,.docx,.ppt,.pptx,.jpg,.jpeg,.png,.bmp,.gif,.msg",*/
				/*accept: "image/*",*/  
                hidden: true
            });
 
            var uploadLabel = $(`
                <label for="${fileUploadId}" class="file-upload">
                    <div class="upload-content text-center text-muted">
                        <i class="material-icons-outlined">cloud_upload</i>
                        <p>Choisir un fichier</p>
                    </div>
                </label>
            `);
 
            inputFile.wrap('<div class="file-container"></div>').after(uploadLabel);
            var container = inputFile.closest('.file-container');
            var uploadContent = uploadLabel.find('.upload-content');
 
			function showFilePreview(file) {
			    const fileType = file.type;
 
			    if (fileType.startsWith("image/")) {
			        const reader = new FileReader();
			        reader.onload = function (e) {
			            uploadContent.html(`
			                <img src="${e.target.result}" alt="${file.name}" class="img-fluid rounded" style="max-height: 200px;">
			            `);
			        };
			        reader.readAsDataURL(file);
			    } else {
			        uploadContent.html(`
			            <i class="material-icons-outlined">insert_drive_file</i>
			            <p>${file.name}</p>
			        `);
			    }
			}
 
            // Drag & Drop on visible area
            uploadLabel.on({
                dragover: function (e) {
                    e.preventDefault();
                    container.addClass("dragover");
                },
                dragleave: function () {
                    container.removeClass("dragover");
                },
                drop: function (e) {
                    e.preventDefault();
                    container.removeClass("dragover");
                    const files = e.originalEvent.dataTransfer.files;
                    if (files.length > 0) {
                        inputFile[0].files = files;
                        showFilePreview(files[0]);
                    }
                }
            });
 
            inputFile.on("change", function () {
                if (this.files.length > 0) {
                    showFilePreview(this.files[0]);
                }
            });
        });
    };
})(jQuery);
 
 