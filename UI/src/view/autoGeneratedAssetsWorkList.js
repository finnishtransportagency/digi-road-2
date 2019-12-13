(function (root) {
  root.AutoGeneratedAssetsWorkList = function() {
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/autoGeneratedAssets";
    this.title = 'Lisätyt viivamaiset kohteet';
    var backend;
    var showFormBtnVisible = true;
    var assetConfig = new AssetTypeConfiguration();

    var availableAssetsTypeId = [190, 210, 430, 420];

    this.initialize = function(mapBackend) {
      backend = mapBackend;
      me.bindEvents();
    };
    
    this.bindEvents = function () {
      eventbus.on('autoGeneratedAssets:select', function() {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        me.generateWorkList(availableAssetsTypeId);
      });
    };

    this.createVerificationForm = function(assetType) {
      $('#tableData').hide();
      $('.filter-box').hide();
      if (showFormBtnVisible) $('#work-list-header').append($('<a class="header-link"></a>').attr('href', me.hrefDir).html('Kuntavalinta').click(function(){
          me.generateWorkList(availableAssetsTypeId);
        })
      );
      me.reloadForm(assetType);
    };

    this.assetTypesTable = function(assetsTypeIds)  {
      var tableContentRows = function(assetTypes) {
        return _.map(assetTypes, function(assetType) {
          return $('<tr/>').append($('<td/>').append(assetLink(assetType)));
        });
      };

      var assetLink = function(assetType) {
        return $('<a class="work-list-item"/>').attr('href', me.hrefDir).html(renameAsset(assetType, assetConfig.assetTypeInfo).title).click(function(){
          me.createVerificationForm(assetType);
        });
      };

      return $('<table id="tableData"><tbody>').append(tableContentRows(assetsTypeIds)).append('</tbody></table>');
    };

    this.createTable = function(assetContent, assetTypeId) {

      var municipalityHeader = function(municipalityName) {
        return $('<h3/>').html(municipalityName);
      };

      var tableBodyRows = function(values) {
        return $('<tbody>').append(tableContentRows(values));
      };

      var tableContentRows = function(ids) {
        return _.map(ids, function(id) {
          return $('<tr/>').append($('<td/>').append(assetLink(id)));
        });
      };

      var assetLink = function(id) {
        var link = '#' + renameAsset(assetTypeId, assetConfig.linearAssetsConfig).singleElementEventCategory + '/' + id;
        return $('<a class="work-list-item"/>').attr('href', link).html(link);
      };

      var tableForGroupingValues = function(assetIds) {
        return $('<table/>').addClass('table')
          .append(tableBodyRows(assetIds));
      };

      return $('<div/>').append(municipalityHeader(assetContent.municipality))
                        .append(tableForGroupingValues(assetContent.generatedAssets));
                                    
    };

    this.reloadForm = function(assetTypeId){
      $('#formTable').remove();
      backend.getautoGeneratedAssets(assetTypeId).then( function(assets){
        $('#work-list .work-list').html($('<h2/>').html(renameAsset(assetTypeId, assetConfig.assetTypeInfo).title)
        .append(_.map(assets, function(assets) { return me.createTable(assets, assetTypeId);})));
      });

    };

    var renameAsset = function (assetTypeId, assetConfigInfo) {
      return _.find(assetConfigInfo, function (config) {
        return config.typeId === assetTypeId;
      });
    };

    this.generateWorkList = function(assetsList) {

      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header id="work-list-header">' + me.title +
        '<a class="header-link" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
        '</header>' +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );


      var element = $('#work-list .work-list');
      element.html($('<div class="linear-asset-list">').append(me.assetTypesTable(assetsList)));

    };

  };
})(this);