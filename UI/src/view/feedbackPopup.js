window.FeedbackPopup = function (message, options) {

  var defaultOptions = {
    type: "confirm",
    saveButton: 'Lähetä',
    cancelButton: 'Peruuta',
    successCallback: function(){},
    closeCallback: function(){},
    container: '.container'
  };

  options = _.merge(defaultOptions, options);

  var confirmDiv =
      '<div class="modal-overlay confirm-modal">' +
          '<div class="modal-dialog">' +
              '<div class="content">' + message + '</div>' +
              '<form class="form form-horizontal" role="form" onsubmit="return saveFormData()">' +
                  '<div class="form-group">' +
                      '<label class="control-label">Palautteen tyyppi</label>' +

                    '<select name="feedbackType" class="form-control">'+
                      '<option value="bug">Bugi</option>'+
                      '<option value="developmentProposal">Kehitysehdotus </option>'+
                      '<option value="freeFeedback">Vapaa palaute</option>'+
                    '</select>'+

                    '<label class="control-label">Otsikko</label>' +
                    '<input type="text" name="headline" class="form-control">' +

                    '<label class="control-label">Palaute</label>' +
                    '<input type="text" name="freeText" class="form-control">' +


                    '<label class="control-label">K-tunnus</label>' +
                    '<input type="text" name="kIdentifier" class="form-control">' +


                    '<label class="control-label">Nimi</label>' +
                    '<input type="text" name="name" class="form-control">' +


                    '<label class="control-label">Sähköposti</label>' +
                    '<input type="text" name="email" class="form-control">' +


                    '<label class="control-label">Puhelinnumero</label>' +
                    '<input type="text" name="phoneNumber" class="form-control">' +

               '</div>' +
              '</form>' +
              '<div class="actions">' +
                  '<button class = "btn btn-primary yes">' + options.saveButton + '</button>' +
                  '<button class = "btn btn-secondary no">' + options.cancelButton + '</button>' +
              '</div>' +
          '</div>' +
    '</div>';


  var renderConfirmDialog = function() {
    jQuery(options.container).append(confirmDiv);
    var modal = $('.modal-dialog');
  };

  var bindEvents = function() {
    jQuery('.confirm-modal .no').on('click', function() {
      purge();
      options.closeCallback();
    });
    jQuery('.confirm-modal .yes').on('click', function() {
      // purge();
      options.successCallback();

      $.ajax({
        type: "POST",
        contentType: "application/json",
        dataType: "json",
        url: "api/feedback",
        data:  convertFromToJSON($(".form-horizontal").serializeArray()),
        success: function()
        {
          options.successCallback();
        },
        error: function (errorValue) {
          if (errorValue.status === 400) {
            alert('Tarkista syöttämäsi tiedot.');
          }
        }
      });

    });
  };

  var convertFromToJSON = function(form){
    var json = {};
    jQuery.each(form, function(){
      json[this.name] = this.value || '';
    });
    return JSON.stringify({body : json});
  };

  var show = function() {
    purge();
    renderConfirmDialog();
    bindEvents();
  };

  var purge = function() {
    jQuery('.confirm-modal').remove();
  };

  show();
};