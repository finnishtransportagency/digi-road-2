(function (root) {
    root.GroupedPointAssetForm = {
        initialize: bindEvents
    };

    function bindEvents(pointAsset, roadCollection, feedbackCollection) {

        var typeIds = pointAsset.typeIds,
            selectedAsset = pointAsset.selectedPointAsset,
            layerName = pointAsset.layerName,
            localizedTexts = pointAsset.formLabels,
            propertiesData = pointAsset.propertyData,
            authorizationPolicy = pointAsset.authorizationPolicy;

        new FeedbackDataTool(feedbackCollection, layerName, authorizationPolicy);

        var rootElement = $('#feature-attributes');

        eventbus.on('layer:selected ' + layerName + ':cancelled roadLinks:fetched', function (layer) {
            if(layer === layerName)
                rootElement.find('.information-content').empty();

            if (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
                renderForm(rootElement, selectedAsset, localizedTexts, typeIds, propertiesData, authorizationPolicy);
            }
        });

        eventbus.on(layerName + ':unselected', function() {
          rootElement.find('#feature-attributes-header').empty();
          rootElement.find('#feature-attributes-form').empty();
          rootElement.find('#feature-attributes-footer').empty();
        });
    }

    function renderForm(rootElement, selectedAsset, localizedTexts, typeIds, propertiesData, authorizationPolicy) {
        var title = localizedTexts.title;
        var header = '<span>' + title + '</span>';
        var form = '';
        var propertyData;

        _.forEach(typeIds, function(typeId) {
            propertyData = _.filter(propertiesData, function (property) {
                return property.propertyTypeId == typeId;
            });

            form += renderAssetFormElements(selectedAsset, typeId, propertyData);
        });

        rootElement.find('#feature-attributes-header').html(header);
        rootElement.find('#feature-attributes-form').html('<div class="wrapper">' + form + '</div>');
        rootElement.find('#feature-attributes-footer').html('');
    }

    function renderAssetFormElements(selectedAsset, typeId, propertyData) {
        var defaultAsset = {'id': '', 'createdBy': '-', 'createdAt': '', 'modifiedBy': '-', 'modifiedAt': '', 'limit': '-'};
        var asset = _.find(selectedAsset.get().assets, function (assets) {
            return assets.typeId == typeId;
        });

        var assetToDisplay = asset ? asset : defaultAsset;

      var informationLog = function (date, username) {
         return date ? (date + ' / ' + username) : '-';
      };

      return '' +
            '  <div class="form form-horizontal form-dark form-pointasset">' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-type-info grouped-assets">' + weightLimitsTypes[typeId] + '</p>' +
            '      <p class="form-control-static asset-type-info"> ID: ' + assetToDisplay.id + '</p>' +
            '    </div>' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n:' + informationLog(assetToDisplay.createdAt, assetToDisplay.createdBy) + '</p>' +
            '    </div>' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-log-info">Muokattu viimeksi:' + (assetToDisplay.modifiedAt ? assetToDisplay.modifiedAt + ' / ': '')  + (assetToDisplay.modifiedBy ? assetToDisplay.modifiedBy : '')  + '</p>' +
            '    </div>' +
            renderValueElement(assetToDisplay, propertyData) +
            '  </div>';
    }

    var weightLimitsTypes = {
        320: 'SUURIN SALLITTU MASSA',
        330: 'YHDISTELMÄN SUURIN SALLITTU MASSA',
        340: 'SUURIN SALLITTU AKSELIMASSA',
        350: 'SUURIN SALLITTU TELIMASSA'
    };

    var numberHandler = function (value, property) {
        return '' +
            '    <div class="form-group editable form-grouped-point">' +
            '        <label class="control-label-grouped-point">' + property.localizedName + '</label>' +
            '        <p class="form-control-static-grouped-point">' + ( (value > 0) ? (value + ' kg') : '-') + '</p>' +
            '    </div>';
    };

    function renderValueElement(asset, propertyData) {
        return _.reduce(_.map(propertyData, function (feature) {
            feature.localizedName = window.localizedStrings[feature.publicId];
            var propertyType = feature.propertyType;

            if (propertyType === "number")
                return numberHandler(asset.limit, feature);

        }), function(prev, curr) { return prev + curr; }, '');
    }
})(this);